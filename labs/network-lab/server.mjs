import http from 'node:http';
import https from 'node:https';
import dns from 'node:dns';
import net from 'node:net';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { performance } from 'node:perf_hooks';
import { performDeepInspection } from './deep-tls.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PUBLIC_DIR = path.join(__dirname, 'public');

export const DEFAULT_TARGET_URL = 'https://day-by-day-one.vercel.app/health';

const PORT = parseInt(process.env.NETWORK_LAB_PORT || '3100', 10);

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
};

/**
 * Validates whether an IP string is publicly routable on the global Internet.
 * Rejects private, loopback, link-local, multicast, unspecified, and reserved ranges.
 */
export function isPubliclyRoutable(ip) {
  if (net.isIPv4(ip)) {
    const parts = ip.split('.').map(Number);
    if (parts.length !== 4 || parts.some((p) => isNaN(p) || p < 0 || p > 255)) return false;
    const [a, b] = parts;
    if (a === 0) return false; // 0.0.0.0/8 (current network)
    if (a === 10) return false; // 10.0.0.0/8 (private)
    if (a === 127) return false; // 127.0.0.0/8 (loopback)
    if (a === 169 && b === 254) return false; // 169.254.0.0/16 (link-local, cloud metadata)
    if (a === 172 && b >= 16 && b <= 31) return false; // 172.16.0.0/12 (private)
    if (a === 192 && b === 168) return false; // 192.168.0.0/16 (private)
    if (a === 100 && b >= 64 && b <= 127) return false; // 100.64.0.0/10 (carrier-grade NAT)
    if (a >= 224) return false; // 224.0.0.0/4 (multicast & reserved)
    if (ip === '255.255.255.255') return false; // broadcast
    return true;
  }

  if (net.isIPv6(ip)) {
    const lower = ip.toLowerCase();
    if (lower === '::' || lower === '::1') return false; // unspecified / loopback
    if (lower.startsWith('::ffff:')) {
      const v4 = lower.slice(7);
      return isPubliclyRoutable(v4);
    }
    // fc00::/7 Unique Local Address
    if (/^f[cd][0-9a-f]{2}:/i.test(lower)) return false;
    // fe80::/10 Link-local Unicast
    if (/^fe[89ab][0-9a-f]:/i.test(lower)) return false;
    // ff00::/8 Multicast
    if (/^ff[0-9a-f]{2}:/i.test(lower)) return false;
    return true;
  }

  return false;
}

/**
 * Validates the input URL for strict Phase 2 safety constraints.
 */
export function validateTargetUrl(rawUrl) {
  if (!rawUrl || typeof rawUrl !== 'string') {
    throw new Error('Missing target URL');
  }

  let parsed;
  try {
    parsed = new URL(rawUrl);
  } catch {
    throw new Error('Invalid URL format: must be a valid absolute HTTPS URL.');
  }

  if (parsed.protocol !== 'https:') {
    throw new Error('Blocked: only https:// URLs are allowed (HTTP is not permitted).');
  }

  if (parsed.username || parsed.password) {
    throw new Error('Blocked: URLs containing usernames or passwords are not permitted.');
  }

  if (parsed.port && parsed.port !== '443') {
    throw new Error(`Blocked: custom port '${parsed.port}' is not permitted. Only standard HTTPS (port 443) is allowed.`);
  }

  const hostname = parsed.hostname.toLowerCase();
  if (!hostname) {
    throw new Error('Invalid URL: missing destination hostname.');
  }

  if (hostname === 'localhost' || hostname.endsWith('.localhost') || hostname.endsWith('.local')) {
    throw new Error('Blocked: destination is a local or loopback address.');
  }

  if (net.isIP(hostname)) {
    if (!isPubliclyRoutable(hostname)) {
      throw new Error('Blocked: destination resolves to a private or local network address.');
    }
  }

  return parsed;
}

/**
 * Formats a Buffer or string into readable hex dump lines.
 */
export function formatHexDump(buffer) {
  const buf = Buffer.isBuffer(buffer) ? buffer : Buffer.from(buffer, 'utf8');
  const lines = [];
  for (let i = 0; i < buf.length; i += 16) {
    const chunk = buf.subarray(i, i + 16);
    const hex = Array.from(chunk).map((b) => b.toString(16).padStart(2, '0')).join(' ');
    const ascii = Array.from(chunk).map((b) => (b >= 32 && b <= 126 ? String.fromCharCode(b) : '.')).join('');
    const offset = i.toString(16).padStart(4, '0');
    lines.push(offset + '  ' + hex.padEnd(48, ' ') + '  |' + ascii + '|');
  }
  return lines.join('\n');
}

export async function performInspection(targetUrl) {
  const result = {
    target: targetUrl.href,
    dns: null,
    tcp: null,
    tls: null,
    http: null,
    response: null,
    applicationBytes: null,
    timings: {},
    error: null,
    failedStage: null,
  };

  const overallStart = performance.now();

  // -------------------------------------------------------------
  // Stage 1: DNS Resolution & Target Safety Validation
  // -------------------------------------------------------------
  let selectedAddress = null;
  try {
    const configuredResolvers = dns.getServers();
    const dnsLookupStart = performance.now();
    const allAddresses = await dns.promises.lookup(targetUrl.hostname, { all: true });
    const dnsLookupEnd = performance.now();

    if (!allAddresses || allAddresses.length === 0) {
      throw new Error(`DNS lookup yielded no addresses for ${targetUrl.hostname}`);
    }

    // Safety check: Ensure every resolved IP is publicly routable
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

  // -------------------------------------------------------------
  // Prepare Outgoing Request Text & Hex Dump (pre-TLS)
  // -------------------------------------------------------------
  const requestPath = (targetUrl.pathname || '/') + (targetUrl.search || '');
  const requestHeaders = {
    'Host': targetUrl.hostname,
    'Connection': 'close',
    'User-Agent': 'DayByDay-NetworkLab/2.0',
    'Accept': 'application/json, text/html, text/plain, */*',
  };

  const constructedRequestText =
    `GET ${requestPath} HTTP/1.1\r\n` +
    Object.entries(requestHeaders).map(([k, v]) => `${k}: ${v}\r\n`).join('') +
    '\r\n';
  const requestHexDump = formatHexDump(Buffer.from(constructedRequestText, 'utf8'));

  // -------------------------------------------------------------
  // Stages 2-5: TCP Connect, TLS Handshake, HTTP, Response
  // -------------------------------------------------------------
  return new Promise((resolve) => {
    let currentStage = 'TCP';
    let tcpStart = 0;
    let tcpEnd = 0;
    let tlsStart = 0;
    let tlsEnd = 0;
    let firstResponseTime = 0;
    let socketRef = null;

    const req = https.request(targetUrl, {
      method: 'GET',
      agent: false, // Fresh socket connection, no connection pooling/keep-alive
      headers: requestHeaders,
      // Pin connection directly to pre-validated public IP to prevent DNS rebinding
      lookup: (_hostname, options, cb) => {
        if (options && options.all) {
          cb(null, [{ address: selectedAddress.address, family: selectedAddress.family }]);
        } else {
          cb(null, selectedAddress.address, selectedAddress.family);
        }
      },
    }, (res) => {
      currentStage = 'RESPONSE';
      firstResponseTime = performance.now();

      const chunks = [];
      let totalBytes = 0;
      const MAX_BODY_BYTES = 65536; // 64 KB limit

      res.on('data', (chunk) => {
        if (totalBytes < MAX_BODY_BYTES) {
          chunks.push(chunk);
          totalBytes += chunk.length;
        }
      });

      res.on('end', () => {
        const totalEnd = performance.now();
        const rawBody = Buffer.concat(chunks).toString('utf8');
        const httpWaitMs = tlsEnd > 0 ? parseFloat((firstResponseTime - tlsEnd).toFixed(2)) : null;
        const responseHexDump = formatHexDump(Buffer.from(rawBody, 'utf8'));

        result.http = {
          status: 'success',
          method: 'GET',
          path: requestPath,
          hostname: targetUrl.hostname,
          httpVersion: res.httpVersion,
          httpVersionLabel: 'HTTP version used by this inspection request',
          httpVersionNote: 'This is the HTTP version used by the local Node inspector for this request. It does not claim that the destination only supports this HTTP version or that a browser would necessarily negotiate the same version.',
        };

        result.response = {
          status: 'success',
          statusCode: res.statusCode,
          statusMessage: res.statusMessage,
          headers: res.headers,
          body: rawBody,
          bodyTruncated: totalBytes >= MAX_BODY_BYTES,
          bodySizeBytes: totalBytes,
        };

        result.applicationBytes = {
          request: {
            text: constructedRequestText,
            hexDump: requestHexDump,
            byteLength: Buffer.byteLength(constructedRequestText, 'utf8'),
            label: 'OBSERVED/CONSTRUCTED FROM THE ACTUAL HTTP REQUEST BEFORE TLS ENCRYPTION',
          },
          response: {
            text: rawBody,
            hexDump: responseHexDump,
            byteLength: totalBytes,
            label: 'PLAINTEXT RESPONSE BODY AFTER TLS DECRYPTION',
          },
          wireNotice: 'Encrypted wire bytes are not captured by this Phase 2 lab.',
        };

        result.timings.httpWaitMs = httpWaitMs;
        result.timings.firstResponseFromStartMs = parseFloat((firstResponseTime - overallStart).toFixed(2));
        result.timings.totalMs = parseFloat((totalEnd - overallStart).toFixed(2));

        resolve(result);
      });
    });

    // 10 second timeout for lab safety
    req.setTimeout(10000, () => {
      req.destroy(new Error('Connection timed out after 10000ms'));
    });

    req.on('socket', (socket) => {
      socketRef = socket;
      tcpStart = performance.now();

      socket.on('connect', () => {
        tcpEnd = performance.now();
        tlsStart = performance.now();
        currentStage = 'TLS';

        const tcpDurationMs = tcpEnd - tcpStart;
        result.tcp = {
          status: 'success',
          localAddress: socket.localAddress,
          localPort: socket.localPort,
          remoteAddress: socket.remoteAddress,
          remotePort: socket.remotePort,
          durationMs: parseFloat(tcpDurationMs.toFixed(2)),
        };
        result.timings.tcpConnectMs = parseFloat(tcpDurationMs.toFixed(2));
      });

      socket.on('secureConnect', () => {
        tlsEnd = performance.now();
        currentStage = 'HTTP';

        const tlsDurationMs = tlsEnd - tlsStart;
        const cert = socket.getPeerCertificate(true);

        // Extract public key using built-in X509Certificate API
        let publicKeyInfo = null;
        try {
          const x509 = typeof socket.getPeerX509Certificate === 'function'
            ? socket.getPeerX509Certificate()
            : (cert.raw ? new crypto.X509Certificate(cert.raw) : null);

          if (x509 && x509.publicKey) {
            const pubKey = x509.publicKey;
            const pem = pubKey.export({ type: 'spki', format: 'pem' });
            const details = pubKey.asymmetricKeyDetails;
            const sanitizedDetails = details
              ? Object.fromEntries(
                  Object.entries(details).map(([k, v]) => [k, typeof v === 'bigint' ? v.toString() : v])
                )
              : null;

            publicKeyInfo = {
              keyType: pubKey.asymmetricKeyType || 'unknown',
              keyDetails: sanitizedDetails,
              pem: pem,
              note: 'This is public information from the server certificate. The corresponding private key stays secret on the server.',
            };
          }
        } catch (err) {
          publicKeyInfo = { error: 'Could not extract public key: ' + err.message };
        }

        result.tls = {
          status: 'success',
          authorized: socket.authorized === true,
          authorizationError: socket.authorizationError || null,
          protocol: socket.getProtocol(),
          cipher: socket.getCipher(),
          alpnProtocol: socket.alpnProtocol || 'none',
          servername: socket.servername || targetUrl.hostname,
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
          publicKey: publicKeyInfo,
          tlsSessionState: 'TLS traffic encryption keys established for this connection.',
          durationMs: parseFloat(tlsDurationMs.toFixed(2)),
        };
        result.timings.tlsHandshakeMs = parseFloat(tlsDurationMs.toFixed(2));
      });
    });

    req.on('error', (err) => {
      result.failedStage = currentStage;
      result.error = err.message;
      result.timings.totalMs = parseFloat((performance.now() - overallStart).toFixed(2));

      if (currentStage === 'TCP' && !result.tcp) {
        result.tcp = { status: 'failed', error: err.message };
      } else if (currentStage === 'TLS' && !result.tls) {
        result.tls = {
          status: 'failed',
          error: err.message,
          authorizationError: socketRef?.authorizationError || null,
        };
      } else if ((currentStage === 'HTTP' || currentStage === 'RESPONSE') && !result.response) {
        result.http = result.http || { status: 'failed', error: err.message };
        result.response = { status: 'failed', error: err.message };
      }

      resolve(result);
    });

    req.end();
  });
}

const server = http.createServer(async (req, res) => {
  const reqUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  // Endpoint: GET /api/inspect?url=...
  if (reqUrl.pathname === '/api/inspect') {
    if (req.method !== 'GET') {
      res.writeHead(405, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Method Not Allowed. Use GET /api/inspect.' }));
      return;
    }

    const rawTarget = reqUrl.searchParams.get('url') || DEFAULT_TARGET_URL;
    let validatedTarget;
    try {
      validatedTarget = validateTargetUrl(rawTarget.trim());
    } catch (err) {
      res.writeHead(400, {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-store',
      });
      res.end(JSON.stringify({
        ok: false,
        error: err.message,
        failedStage: 'TARGET_VALIDATION',
        target: rawTarget,
      }));
      return;
    }

    try {
      const inspectionData = await performInspection(validatedTarget);
      const statusCode = inspectionData.error && inspectionData.failedStage === 'TARGET_VALIDATION' ? 400 : 200;
      res.writeHead(statusCode, {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-store, no-cache, must-revalidate',
      });
      res.end(JSON.stringify(inspectionData, null, 2));
    } catch (err) {
      res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({
        ok: false,
        error: 'Inspection failed with unexpected error',
        message: err.message,
      }));
    }
    return;
  }

  // Endpoint: GET /api/deep-inspect?url=...
  if (reqUrl.pathname === '/api/deep-inspect') {
    if (req.method !== 'GET') {
      res.writeHead(405, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Method Not Allowed. Use GET /api/deep-inspect.' }));
      return;
    }

    const rawTarget = reqUrl.searchParams.get('url') || DEFAULT_TARGET_URL;
    let validatedTarget;
    try {
      validatedTarget = validateTargetUrl(rawTarget.trim());
    } catch (err) {
      res.writeHead(400, {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-store',
      });
      res.end(JSON.stringify({
        ok: false,
        error: err.message,
        failedStage: 'TARGET_VALIDATION',
        target: rawTarget,
      }));
      return;
    }

    try {
      const deepData = await performDeepInspection(validatedTarget);
      const statusCode = deepData.error && deepData.failedStage === 'TARGET_VALIDATION' ? 400 : 200;
      res.writeHead(statusCode, {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-store',
      });
      res.end(JSON.stringify(deepData, null, 2));
    } catch (err) {
      res.writeHead(500, {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-store',
      });
      res.end(JSON.stringify({
        ok: false,
        error: 'Deep TLS inspection failed with unexpected error',
        message: err.message,
      }));
    }
    return;
  }

  // Static file serving from public/
  if (req.method === 'GET' || req.method === 'HEAD') {
    let filePath = reqUrl.pathname;
    if (filePath === '/' || filePath === '') {
      filePath = '/index.html';
    }

    // Prevent directory traversal
    const safePath = path.normalize(filePath).replace(/^(\.\.[/\\])+/, '');
    const fullPath = path.join(PUBLIC_DIR, safePath);

    if (!fullPath.startsWith(PUBLIC_DIR)) {
      res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Access Denied');
      return;
    }

    fs.stat(fullPath, (err, stats) => {
      if (err || !stats.isFile()) {
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('404 Not Found');
        return;
      }

      const ext = path.extname(fullPath).toLowerCase();
      const contentType = MIME_TYPES[ext] || 'application/octet-stream';

      res.writeHead(200, {
        'Content-Type': contentType,
        'Content-Length': stats.size,
        'Cache-Control': 'no-cache',
      });

      if (req.method === 'HEAD') {
        res.end();
        return;
      }

      const stream = fs.createReadStream(fullPath);
      stream.pipe(res);
      stream.on('error', () => {
        if (!res.headersSent) {
          res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
          res.end('Internal Server Error');
        }
      });
    });
    return;
  }

  res.writeHead(405, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('Method Not Allowed');
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Networking Learning Lab server listening on http://127.0.0.1:${PORT}`);
});
