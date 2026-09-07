import net from 'node:net';
import tls from 'node:tls';
import dns from 'node:dns';
import crypto from 'node:crypto';
import { Duplex } from 'node:stream';
import { performance } from 'node:perf_hooks';
import { isPubliclyRoutable, formatHexDump } from './server.mjs';

const TLS13_CIPHER_SPECS = {
  'TLS_AES_128_GCM_SHA256': { hash: 'sha256', keyLen: 16, ivLen: 12 },
  'TLS_AES_256_GCM_SHA384': { hash: 'sha384', keyLen: 32, ivLen: 12 },
  'TLS_CHACHA20_POLY1305_SHA256': { hash: 'sha256', keyLen: 32, ivLen: 12 },
};

/**
 * Implements HKDF-Expand according to RFC 5869 / RFC 8446.
 */
function hkdfExpand(hash, prk, info, length) {
  const hashLen = crypto.createHash(hash).digest().length;
  const n = Math.ceil(length / hashLen);
  let okm = Buffer.alloc(0);
  let t = Buffer.alloc(0);

  for (let i = 1; i <= n; i++) {
    const hmac = crypto.createHmac(hash, prk);
    hmac.update(t);
    hmac.update(info);
    hmac.update(Buffer.from([i]));
    t = hmac.digest();
    okm = Buffer.concat([okm, t]);
  }

  return okm.subarray(0, length);
}

/**
 * Implements TLS 1.3 HKDF-Expand-Label according to RFC 8446 Section 7.1.
 * HKDF-Expand-Label(Secret, Label, Context, Length) = HKDF-Expand(Secret, HkdfLabel, Length)
 */
export function hkdfExpandLabel(hash, secret, label, context, length) {
  const fullLabel = Buffer.from('tls13 ' + label, 'utf8');
  const contextBuf = Buffer.isBuffer(context) ? context : Buffer.from(context || '', 'utf8');

  const lengthBuf = Buffer.alloc(2);
  lengthBuf.writeUInt16BE(length, 0);

  const labelLenBuf = Buffer.from([fullLabel.length]);
  const contextLenBuf = Buffer.from([contextBuf.length]);

  const hkdfLabel = Buffer.concat([
    lengthBuf,
    labelLenBuf,
    fullLabel,
    contextLenBuf,
    contextBuf,
  ]);

  return hkdfExpand(hash, secret, hkdfLabel, length);
}

/**
 * Derives TLS 1.3 application traffic keys and IVs using RFC 8446 key schedule.
 */
export function deriveTls13ApplicationKeys(cipherName, clientSecretHex, serverSecretHex) {
  const spec = TLS13_CIPHER_SPECS[cipherName];
  if (!spec) {
    return {
      supported: false,
      message: 'Detailed application-key derivation is implemented for supported TLS 1.3 connections only.',
    };
  }

  if (!clientSecretHex || !serverSecretHex) {
    return {
      supported: false,
      message: 'Application traffic secrets (CLIENT_TRAFFIC_SECRET_0 / SERVER_TRAFFIC_SECRET_0) were not emitted for this connection.',
    };
  }

  try {
    const clientSecretBuf = Buffer.from(clientSecretHex, 'hex');
    const serverSecretBuf = Buffer.from(serverSecretHex, 'hex');

    const clientKey = hkdfExpandLabel(spec.hash, clientSecretBuf, 'key', '', spec.keyLen);
    const clientIv = hkdfExpandLabel(spec.hash, clientSecretBuf, 'iv', '', spec.ivLen);

    const serverKey = hkdfExpandLabel(spec.hash, serverSecretBuf, 'key', '', spec.keyLen);
    const serverIv = hkdfExpandLabel(spec.hash, serverSecretBuf, 'iv', '', spec.ivLen);

    return {
      supported: true,
      cipher: cipherName,
      hashAlgorithm: spec.hash,
      keyLengthBytes: spec.keyLen,
      ivLengthBytes: spec.ivLen,
      clientToServer: {
        trafficSecret: clientSecretHex,
        trafficSecretByteLength: clientSecretBuf.length,
        derivedKeyHex: clientKey.toString('hex'),
        derivedKeyLengthBytes: clientKey.length,
        derivedIvHex: clientIv.toString('hex'),
        derivedIvLengthBytes: clientIv.length,
      },
      serverToClient: {
        trafficSecret: serverSecretHex,
        trafficSecretByteLength: serverSecretBuf.length,
        derivedKeyHex: serverKey.toString('hex'),
        derivedKeyLengthBytes: serverKey.length,
        derivedIvHex: serverIv.toString('hex'),
        derivedIvLengthBytes: serverIv.length,
      },
      explanation: 'These are connection-specific. Another HTTPS connection normally derives different values.',
    };
  } catch (err) {
    return {
      supported: false,
      error: `Derivation error: ${err.message}`,
    };
  }
}

/**
 * Parses basic outer TLS 1.3 record boundaries from a captured byte buffer.
 */
export function parseTlsRecords(buffer) {
  const CONTENT_TYPES = {
    20: 'change_cipher_spec (20)',
    21: 'alert (21)',
    22: 'handshake (22)',
    23: 'application_data (23)',
  };

  const records = [];
  let offset = 0;
  const maxRecords = 30; // Reasonable bounded count

  while (offset + 5 <= buffer.length && records.length < maxRecords) {
    const type = buffer.readUInt8(offset);
    const major = buffer.readUInt8(offset + 1);
    const minor = buffer.readUInt8(offset + 2);
    const length = buffer.readUInt16BE(offset + 3);

    // TLS record header sanity check (type 20-25, major version 3)
    if (type >= 20 && type <= 25 && major === 3) {
      records.push({
        recordIndex: records.length + 1,
        offsetBytes: offset,
        outerContentType: CONTENT_TYPES[type] || `unknown (${type})`,
        legacyRecordVersion: `0x0${major}0${minor}`,
        payloadLengthBytes: length,
        totalRecordSizeBytes: 5 + length,
      });
      offset += 5 + length;
    } else {
      break;
    }
  }

  return {
    parsedRecords: records,
    parsedRecordCount: records.length,
    coveredBytes: offset,
    totalCapturedBytes: buffer.length,
    notice: 'These are real TLS bytes observed between Node\'s TLS layer and the TCP connection. They include TLS protocol records and may include handshake/control data in addition to encrypted application data.',
  };
}

/**
 * Performs Phase 3 Deep TLS Inspection.
 * Creates raw TCP connection, hooks capture Duplex, wraps with TLS, intercepts keylog,
 * captures real TLS bytes, sends one HTTP/1.1 request, and decrypts response.
 */
export async function performDeepInspection(targetUrl) {
  const overallStart = performance.now();

  const result = {
    target: targetUrl.href,
    inspectionType: 'deep-tls',
    dns: null,
    tcp: null,
    tls: null,
    trafficSecrets: null,
    derivedKeys: null,
    plaintextRequest: null,
    capturedTlsBytes: null,
    decryptedResponse: null,
    timings: {},
    error: null,
    failedStage: null,
  };

  // 1. DNS Resolution & Safety Validation (exact same logic)
  let selectedAddress = null;
  try {
    const configuredResolvers = dns.getServers();
    const dnsLookupStart = performance.now();
    const allAddresses = await dns.promises.lookup(targetUrl.hostname, { all: true });
    const dnsLookupEnd = performance.now();

    if (!allAddresses || allAddresses.length === 0) {
      throw new Error(`DNS lookup yielded no addresses for ${targetUrl.hostname}`);
    }

    for (const addr of allAddresses) {
      if (!isPubliclyRoutable(addr.address)) {
        throw new Error('Blocked: destination resolves to a private or local network address.');
      }
    }

    selectedAddress = allAddresses[0];
    const dnsDurationMs = dnsLookupEnd - dnsLookupStart;

    result.dns = {
      status: 'success',
      hostname: targetUrl.hostname,
      configuredResolvers: configuredResolvers.length > 0 ? configuredResolvers : ['OS default resolver'],
      resolvedAddress: selectedAddress.address,
      family: selectedAddress.family === 6 ? 'IPv6' : 'IPv4',
      allAddresses,
      durationMs: parseFloat(dnsDurationMs.toFixed(2)),
      lookupNote: 'This is the lookup time observed by this Mac. The answer may have been served from an operating-system or DNS-resolver cache.',
    };
    result.timings.dnsLookupMs = parseFloat(dnsDurationMs.toFixed(2));
  } catch (err) {
    result.dns = {
      status: 'failed',
      hostname: targetUrl.hostname,
      error: err.message,
    };
    result.failedStage = err.message.startsWith('Blocked:') ? 'TARGET_VALIDATION' : 'DNS';
    result.error = err.message;
    result.timings.totalMs = parseFloat((performance.now() - overallStart).toFixed(2));
    return result;
  }

  // 2. Prepare Outgoing Plaintext HTTP/1.1 Request
  const requestPath = (targetUrl.pathname || '/') + (targetUrl.search || '');
  const requestHeaders = {
    'Host': targetUrl.hostname,
    'User-Agent': 'DayByDay-NetworkLab/3.0',
    'Accept': 'application/json, text/html, text/plain, */*',
    'Accept-Encoding': 'identity',
    'Connection': 'close',
  };

  const plaintextRequestText =
    `GET ${requestPath} HTTP/1.1\r\n` +
    Object.entries(requestHeaders).map(([k, v]) => `${k}: ${v}\r\n`).join('') +
    '\r\n';
  const plaintextRequestBuf = Buffer.from(plaintextRequestText, 'utf8');

  result.plaintextRequest = {
    text: plaintextRequestText,
    byteLength: plaintextRequestBuf.length,
    hexDump: formatHexDump(plaintextRequestBuf),
    label: 'Plaintext application bytes given TO TLS',
  };

  // 3. Raw TCP connect + Duplex capture layer + TLS connect
  return new Promise((resolve) => {
    let currentStage = 'TCP';
    let tcpStart = performance.now();
    let tcpEnd = 0;
    let tlsStart = 0;
    let tlsEnd = 0;
    let firstResponseTime = 0;

    const MAX_CAPTURE_BYTES = 65536; // 64 KB display/capture limit
    const outboundChunks = [];
    const inboundChunks = [];
    let outboundTotalBytes = 0;
    let inboundTotalBytes = 0;

    const keylogLines = [];
    const keylogSecrets = {};
    const decryptedChunks = [];
    let decryptedTotalBytes = 0;

    const rawTcp = net.connect({
      host: selectedAddress.address,
      port: 443,
    });

    const captureDuplex = new Duplex({
      write(chunk, encoding, callback) {
        if (outboundTotalBytes < MAX_CAPTURE_BYTES) {
          outboundChunks.push(Buffer.from(chunk));
        }
        outboundTotalBytes += chunk.length;
        rawTcp.write(chunk, encoding, callback);
      },
      read() {},
    });

    rawTcp.on('data', (chunk) => {
      if (inboundTotalBytes < MAX_CAPTURE_BYTES) {
        inboundChunks.push(Buffer.from(chunk));
      }
      inboundTotalBytes += chunk.length;
      captureDuplex.push(chunk);
    });

    rawTcp.on('end', () => {
      captureDuplex.push(null);
    });

    rawTcp.on('error', (err) => {
      captureDuplex.destroy(err);
    });

    rawTcp.on('connect', () => {
      tcpEnd = performance.now();
      tlsStart = performance.now();
      currentStage = 'TLS';

      const tcpDurationMs = tcpEnd - tcpStart;
      result.tcp = {
        status: 'success',
        localAddress: rawTcp.localAddress,
        localPort: rawTcp.localPort,
        remoteAddress: rawTcp.remoteAddress,
        remotePort: rawTcp.remotePort,
        durationMs: parseFloat(tcpDurationMs.toFixed(2)),
      };
      result.timings.tcpConnectMs = parseFloat(tcpDurationMs.toFixed(2));
    });

    const tlsSocket = tls.connect({
      socket: captureDuplex,
      servername: targetUrl.hostname,
      rejectUnauthorized: true,
      ALPNProtocols: ['http/1.1'],
    });

    // Capture NSS-style keylog lines in memory for this connection only
    tlsSocket.on('keylog', (lineBuf) => {
      const lineStr = lineBuf.toString('utf8').trim();
      if (!lineStr) return;
      keylogLines.push(lineStr);

      const parts = lineStr.split(/\s+/);
      if (parts.length >= 3) {
        keylogSecrets[parts[0]] = parts[2];
      }
    });

    tlsSocket.on('secureConnect', () => {
      tlsEnd = performance.now();
      currentStage = 'HTTP';

      const tlsDurationMs = tlsEnd - tlsStart;
      const cert = tlsSocket.getPeerCertificate(true);
      const cipher = tlsSocket.getCipher();

      result.tls = {
        status: 'success',
        authorized: tlsSocket.authorized === true,
        authorizationError: tlsSocket.authorizationError || null,
        protocol: tlsSocket.getProtocol(),
        cipher: cipher,
        alpnProtocol: tlsSocket.alpnProtocol || 'http/1.1',
        servername: tlsSocket.servername || targetUrl.hostname,
        certificate: {
          subject: cert.subject ? cert.subject.CN || cert.subject : null,
          subjectAltName: cert.subjectaltname || null,
          issuer: cert.issuer ? cert.issuer.O || cert.issuer.CN || JSON.stringify(cert.issuer) : null,
          issuerFull: cert.issuer,
          validFrom: cert.valid_from,
          validTo: cert.valid_to,
          fingerprint256: cert.fingerprint256,
          serialNumber: cert.serialNumber,
        },
        durationMs: parseFloat(tlsDurationMs.toFixed(2)),
      };
      result.timings.tlsHandshakeMs = parseFloat(tlsDurationMs.toFixed(2));

      // Send the plaintext HTTP request through the TLS socket
      tlsSocket.write(plaintextRequestBuf);
    });

    tlsSocket.on('data', (chunk) => {
      if (firstResponseTime === 0) {
        firstResponseTime = performance.now();
        currentStage = 'RESPONSE';
      }
      if (decryptedTotalBytes < MAX_CAPTURE_BYTES) {
        decryptedChunks.push(chunk);
      }
      decryptedTotalBytes += chunk.length;
    });

    tlsSocket.on('end', () => {
      const totalEnd = performance.now();
      const httpWaitMs = tlsEnd > 0 && firstResponseTime > 0 ? parseFloat((firstResponseTime - tlsEnd).toFixed(2)) : null;

      // Close underlying TCP socket
      rawTcp.end();

      const outboundBuf = Buffer.concat(outboundChunks);
      const inboundBuf = Buffer.concat(inboundChunks);
      const decryptedBuf = Buffer.concat(decryptedChunks);

      // Parse HTTP response
      const decryptedStr = decryptedBuf.toString('utf8');
      const headerEndIdx = decryptedBuf.indexOf('\r\n\r\n');
      let statusLine = '';
      let statusCode = 200;
      let headersObj = {};
      let bodyStr = '';

      if (headerEndIdx !== -1) {
        const headerText = decryptedBuf.subarray(0, headerEndIdx).toString('utf8');
        bodyStr = decryptedBuf.subarray(headerEndIdx + 4).toString('utf8');
        const headerLines = headerText.split('\r\n');
        statusLine = headerLines[0] || '';
        const match = statusLine.match(/HTTP\/\S+\s+(\d+)/);
        if (match) {
          statusCode = parseInt(match[1], 10);
        }
        for (let i = 1; i < headerLines.length; i++) {
          const colonIdx = headerLines[i].indexOf(':');
          if (colonIdx !== -1) {
            const key = headerLines[i].slice(0, colonIdx).trim().toLowerCase();
            const val = headerLines[i].slice(colonIdx + 1).trim();
            headersObj[key] = val;
          }
        }
      } else {
        bodyStr = decryptedStr;
      }

      result.http = {
        status: 'success',
        method: 'GET',
        path: requestPath,
        hostname: targetUrl.hostname,
        httpVersion: '1.1',
      };

      result.decryptedResponse = {
        status: 'success',
        statusLine: statusLine,
        statusCode: statusCode,
        headers: headersObj,
        body: bodyStr,
        fullPlaintext: decryptedStr,
        byteLength: decryptedTotalBytes,
        truncated: decryptedTotalBytes > MAX_CAPTURE_BYTES,
        hexDump: formatHexDump(decryptedBuf),
        label: 'Plaintext application bytes received FROM TLS',
      };

      // TLS 1.3 Traffic Secrets
      result.trafficSecrets = {
        keylogRawLines: keylogLines,
        labelsCaptured: Object.keys(keylogSecrets),
        secrets: keylogSecrets,
        warning: 'Anyone who has these secrets together with the captured TLS traffic may be able to decrypt this connection. These values exist here only for this local learning experiment.',
      };

      // Derived Symmetric Application Keys
      const cipherName = result.tls?.cipher?.name || result.tls?.cipher || '';
      result.derivedKeys = deriveTls13ApplicationKeys(
        cipherName,
        keylogSecrets['CLIENT_TRAFFIC_SECRET_0'],
        keylogSecrets['SERVER_TRAFFIC_SECRET_0']
      );

      // Actual Captured TLS Bytes
      result.capturedTlsBytes = {
        outbound: {
          totalByteCount: outboundTotalBytes,
          capturedByteCount: outboundBuf.length,
          truncated: outboundTotalBytes > MAX_CAPTURE_BYTES,
          hexDump: formatHexDump(outboundBuf),
          recordAnalysis: parseTlsRecords(outboundBuf),
          label: 'OUTBOUND TLS BYTES (TLS → TCP)',
        },
        inbound: {
          totalByteCount: inboundTotalBytes,
          capturedByteCount: inboundBuf.length,
          truncated: inboundTotalBytes > MAX_CAPTURE_BYTES,
          hexDump: formatHexDump(inboundBuf),
          recordAnalysis: parseTlsRecords(inboundBuf),
          label: 'INBOUND TLS BYTES (TCP → TLS)',
        },
        notice: 'These are real TLS bytes observed between Node\'s TLS layer and the TCP connection. They include TLS protocol records and may include handshake/control data in addition to encrypted application data.',
      };

      result.timings.httpWaitMs = httpWaitMs;
      result.timings.firstResponseFromStartMs = firstResponseTime > 0 ? parseFloat((firstResponseTime - overallStart).toFixed(2)) : null;
      result.timings.totalMs = parseFloat((totalEnd - overallStart).toFixed(2));

      resolve(result);
    });

    const handleError = (err) => {
      rawTcp.destroy();
      captureDuplex.destroy();

      result.failedStage = currentStage;
      result.error = err.message;
      result.timings.totalMs = parseFloat((performance.now() - overallStart).toFixed(2));

      if (currentStage === 'TCP' && !result.tcp) {
        result.tcp = { status: 'failed', error: err.message };
      } else if (currentStage === 'TLS' && !result.tls) {
        result.tls = { status: 'failed', error: err.message };
      }

      resolve(result);
    };

    tlsSocket.on('error', handleError);
    rawTcp.on('error', handleError);

    // 15 second safety timeout
    rawTcp.setTimeout(15000, () => {
      handleError(new Error('Connection timed out after 15000ms'));
    });
  });
}
