const inspectBtn = document.getElementById('inspect-btn');
const deepInspectBtn = document.getElementById('deep-inspect-btn');
const targetInput = document.getElementById('target-input');
const statusBanner = document.getElementById('status-banner');

const stages = {
  dns: {
    card: document.getElementById('stage-dns'),
    status: document.getElementById('dns-status'),
    summary: document.getElementById('dns-summary'),
    details: document.getElementById('dns-details-content'),
    accordion: document.getElementById('dns-details'),
  },
  tcp: {
    card: document.getElementById('stage-tcp'),
    status: document.getElementById('tcp-status'),
    summary: document.getElementById('tcp-summary'),
    details: document.getElementById('tcp-details-content'),
    accordion: document.getElementById('tcp-details'),
  },
  tls: {
    card: document.getElementById('stage-tls'),
    status: document.getElementById('tls-status'),
    summary: document.getElementById('tls-summary'),
    details: document.getElementById('tls-details-content'),
    accordion: document.getElementById('tls-details'),
    pkAccordion: document.getElementById('public-key-accordion'),
    pkContent: document.getElementById('public-key-details-content'),
  },
  http: {
    card: document.getElementById('stage-http'),
    status: document.getElementById('http-status'),
    summary: document.getElementById('http-summary'),
    details: document.getElementById('http-details-content'),
    accordion: document.getElementById('http-details'),
  },
  response: {
    card: document.getElementById('stage-response'),
    status: document.getElementById('response-status'),
    summary: document.getElementById('response-summary'),
    details: document.getElementById('response-details-content'),
    accordion: document.getElementById('response-details'),
  },
};

const boundaryElements = {
  reqBadge: document.getElementById('req-byte-badge'),
  resBadge: document.getElementById('res-byte-badge'),
  reqPlain: document.getElementById('req-plaintext'),
  reqHex: document.getElementById('req-hexdump'),
  resPlain: document.getElementById('res-plaintext'),
  resHex: document.getElementById('res-hexdump'),
};

const deepElements = {
  flowReqLine: document.getElementById('flow-req-line'),
  reqPlainBadge: document.getElementById('deep-req-plain-badge'),
  reqPlainText: document.getElementById('deep-req-plain-text'),
  reqPlainHex: document.getElementById('deep-req-plain-hex'),
  outboundBadge: document.getElementById('deep-outbound-tls-badge'),
  outboundRecords: document.getElementById('deep-outbound-tls-records'),
  outboundHex: document.getElementById('deep-outbound-tls-hex'),
  inboundBadge: document.getElementById('deep-inbound-tls-badge'),
  inboundRecords: document.getElementById('deep-inbound-tls-records'),
  inboundHex: document.getElementById('deep-inbound-tls-hex'),
  resPlainBadge: document.getElementById('deep-res-plain-badge'),
  resHeaders: document.getElementById('deep-res-headers'),
  resPlainBody: document.getElementById('deep-res-plain-body'),
  resPlainHex: document.getElementById('deep-res-plain-hex'),
  secretsBadge: document.getElementById('deep-secrets-badge'),
  secretsContent: document.getElementById('deep-secrets-content'),
  derivedBadge: document.getElementById('deep-derived-badge'),
  derivedContent: document.getElementById('deep-derived-content'),
};

const timingElements = {
  dns: document.getElementById('time-dns'),
  tcp: document.getElementById('time-tcp'),
  tls: document.getElementById('time-tls'),
  httpWait: document.getElementById('time-http-wait'),
  total: document.getElementById('time-total'),
};

function setStageStatus(stageKey, statusText) {
  const stage = stages[stageKey];
  if (!stage) return;
  stage.status.textContent = statusText;
  stage.status.className = `status-pill ${statusText}`;
  stage.card.className = `stage-card is-${statusText}`;
}

function resetUI() {
  statusBanner.className = 'status-banner hidden';
  statusBanner.textContent = '';

  for (const key of Object.keys(stages)) {
    setStageStatus(key, 'waiting');
    stages[key].summary.textContent = 'Awaiting inspection...';
    stages[key].details.textContent = 'No data yet.';
    stages[key].accordion.removeAttribute('open');
  }

  if (stages.tls.pkAccordion) {
    stages.tls.pkAccordion.removeAttribute('open');
    stages.tls.pkContent.innerHTML = `
      <p class="crypto-note">This is public information from the server certificate. The corresponding private key stays secret on the server.</p>
      <div id="public-key-info">No key loaded yet.</div>
    `;
  }

  boundaryElements.reqBadge.textContent = '0 bytes';
  boundaryElements.resBadge.textContent = '0 bytes';
  boundaryElements.reqPlain.textContent = 'Awaiting inspection...';
  boundaryElements.reqHex.textContent = 'Awaiting inspection...';
  boundaryElements.resPlain.textContent = 'Awaiting inspection...';
  boundaryElements.resHex.textContent = 'Awaiting inspection...';

  if (deepElements.reqPlainBadge) {
    deepElements.flowReqLine.textContent = 'GET /health HTTP/1.1';
    deepElements.reqPlainBadge.textContent = '0 bytes';
    deepElements.reqPlainText.textContent = 'Awaiting deep inspection...';
    deepElements.reqPlainHex.textContent = 'Awaiting deep inspection...';
    deepElements.outboundBadge.textContent = '0 bytes';
    deepElements.outboundRecords.textContent = 'Awaiting deep inspection...';
    deepElements.outboundHex.textContent = 'Awaiting deep inspection...';
    deepElements.inboundBadge.textContent = '0 bytes';
    deepElements.inboundRecords.textContent = 'Awaiting deep inspection...';
    deepElements.inboundHex.textContent = 'Awaiting deep inspection...';
    deepElements.resPlainBadge.textContent = '0 bytes';
    deepElements.resHeaders.textContent = 'Awaiting deep inspection...';
    deepElements.resPlainBody.textContent = 'Awaiting deep inspection...';
    deepElements.resPlainHex.textContent = 'Awaiting deep inspection...';
    deepElements.secretsBadge.textContent = '0 secrets';
    deepElements.secretsContent.textContent = 'Run Deep TLS Inspection to capture raw TLS wire bytes and session secrets.';
    deepElements.derivedBadge.textContent = 'HKDF-Expand-Label';
    deepElements.derivedContent.textContent = 'Run Deep TLS Inspection to derive TLS 1.3 symmetric application keys.';
  }

  timingElements.dns.textContent = '— ms';
  timingElements.tcp.textContent = '— ms';
  timingElements.tls.textContent = '— ms';
  timingElements.httpWait.textContent = '— ms';
  timingElements.total.textContent = '— ms';
}

function formatJson(data) {
  try {
    return JSON.stringify(data, null, 2);
  } catch {
    return String(data);
  }
}

async function runInspection(isDeep = false) {
  const targetUrl = (targetInput.value || '').trim();
  if (!targetUrl) {
    statusBanner.className = 'status-banner error';
    statusBanner.textContent = 'Please enter a valid target URL.';
    return;
  }

  inspectBtn.disabled = true;
  deepInspectBtn.disabled = true;
  if (isDeep) {
    deepInspectBtn.textContent = 'Inspecting Deep TLS...';
  } else {
    inspectBtn.textContent = 'Inspecting...';
  }

  resetUI();

  // Mark all stages running initially
  for (const key of Object.keys(stages)) {
    setStageStatus(key, 'running');
  }

  try {
    const endpoint = isDeep ? '/api/deep-inspect' : '/api/inspect';
    const fetchUrl = `${endpoint}?url=${encodeURIComponent(targetUrl)}`;
    const res = await fetch(fetchUrl);
    const data = await res.json().catch(() => null);

    if (!data) {
      throw new Error(`Server returned HTTP ${res.status} without valid JSON.`);
    }

    // Handle early target validation or DNS rejection
    if (!res.ok || data.failedStage === 'TARGET_VALIDATION') {
      const errorMsg = data.error || `Request failed with HTTP ${res.status}`;
      statusBanner.className = 'status-banner error';
      statusBanner.textContent = errorMsg;

      setStageStatus('dns', 'failed');
      stages.dns.summary.textContent = `Validation / Safety check failed:\n${errorMsg}`;
      stages.dns.details.textContent = formatJson(data);

      setStageStatus('tcp', 'waiting');
      stages.tcp.summary.textContent = 'Connection blocked before TCP socket creation.';

      setStageStatus('tls', 'waiting');
      stages.tls.summary.textContent = 'Connection blocked before TLS handshake.';

      setStageStatus('http', 'waiting');
      stages.http.summary.textContent = 'Connection blocked before HTTP request.';

      setStageStatus('response', 'waiting');
      stages.response.summary.textContent = 'Connection blocked before response.';

      if (data.timings?.totalMs) {
        timingElements.total.textContent = `${data.timings.totalMs} ms`;
      }
      return;
    }

    // 1. DNS Stage
    if (data.dns) {
      if (data.dns.status === 'success') {
        setStageStatus('dns', 'success');
        stages.dns.summary.textContent =
          `Hostname: ${data.dns.hostname}\n` +
          `Resolved Destination IP: ${data.dns.resolvedAddress} (${data.dns.family})\n` +
          `Machine Configured DNS Resolver(s): ${data.dns.configuredResolvers.join(', ')}\n` +
          `Lookup Duration: ${data.dns.durationMs} ms\n` +
          `Note: This is the lookup time observed by this Mac. The answer may have been served from an operating-system or DNS-resolver cache.`;

        stages.dns.details.textContent = formatJson({
          hostname: data.dns.hostname,
          resolvedAddress: data.dns.resolvedAddress,
          ipFamily: data.dns.family,
          allResolvedAddresses: data.dns.allAddresses,
          machineConfiguredResolvers: data.dns.configuredResolvers,
          noteOnResolvers: "These are this machine's local configured DNS resolvers, not necessarily the authoritative nameservers.",
          lookupDurationMs: data.dns.durationMs,
          dnsTimingNote: 'This is the lookup time observed by this Mac. The answer may have been served from an operating-system or DNS-resolver cache.',
          routingPinningNote: 'Destination IP pinned directly to prevent DNS rebinding attacks.',
        });
      } else {
        setStageStatus('dns', 'failed');
        stages.dns.summary.textContent = `DNS lookup failed: ${data.dns.error || 'Unknown error'}`;
        stages.dns.details.textContent = formatJson(data.dns);
      }
    } else {
      setStageStatus('dns', 'failed');
    }

    // 2. TCP Stage
    if (data.tcp) {
      if (data.tcp.status === 'success') {
        setStageStatus('tcp', 'success');
        stages.tcp.summary.textContent =
          `Socket Connection Established:\n` +
          `my-mac:${data.tcp.localPort}  →  ${data.tcp.remoteAddress}:${data.tcp.remotePort}\n` +
          `TCP Connect Duration: ${data.tcp.durationMs} ms`;

        stages.tcp.details.textContent = formatJson({
          localAddress: data.tcp.localAddress,
          localSourcePort: data.tcp.localPort,
          remoteDestinationAddress: data.tcp.remoteAddress,
          remoteDestinationPort: data.tcp.remotePort,
          connectDurationMs: data.tcp.durationMs,
          routingObservation: 'Destination IP observed. IP routing between your Mac and the destination happens across network infrastructure, but this lab does not inspect every router hop.',
        });
      } else {
        setStageStatus('tcp', 'failed');
        stages.tcp.summary.textContent = `TCP connection failed: ${data.tcp.error || 'Connection error'}`;
        stages.tcp.details.textContent = formatJson(data.tcp);
      }
    } else if (data.failedStage === 'TCP') {
      setStageStatus('tcp', 'failed');
      stages.tcp.summary.textContent = `TCP failed: ${data.error}`;
      stages.tcp.details.textContent = data.error;
    } else if (!data.dns || data.dns.status !== 'success') {
      setStageStatus('tcp', 'waiting');
      stages.tcp.summary.textContent = 'Skipped due to prior DNS failure.';
    }

    // 3. TLS Stage
    if (data.tls) {
      if (data.tls.status === 'success') {
        setStageStatus('tls', 'success');
        const certCN = data.tls.certificate?.subject || 'Unknown CN';
        const issuerName = data.tls.certificate?.issuer || 'Unknown Issuer';
        const cipherName = data.tls.cipher?.name || data.tls.cipher || 'Unknown Cipher';

        stages.tls.summary.textContent =
          `Protocol: ${data.tls.protocol} | Cipher: ${cipherName}\n` +
          `SNI Server Name: ${data.tls.servername}\n` +
          `Server Certificate CN: ${certCN} (Issuer: ${issuerName})\n` +
          `TLS Verification: ${data.tls.authorized ? 'Authorized (Strict Certificate Verification Verified)' : 'Verification Failed'}\n` +
          `State: ${data.tls.tlsSessionState || 'TLS traffic encryption keys established for this connection.'}\n` +
          `Handshake Duration: ${data.tls.durationMs} ms`;

        stages.tls.details.textContent = formatJson({
          authorized: data.tls.authorized,
          authorizationError: data.tls.authorizationError,
          protocol: data.tls.protocol,
          cipher: data.tls.cipher,
          alpnProtocol: data.tls.alpnProtocol,
          servernameSNI: data.tls.servername,
          certificateSummary: {
            subject: data.tls.certificate?.subject,
            subjectAltNames: data.tls.certificate?.subjectAltName,
            issuer: data.tls.certificate?.issuer,
            issuerFull: data.tls.certificate?.issuerFull,
            validFrom: data.tls.certificate?.validFrom,
            validTo: data.tls.certificate?.validTo,
            fingerprintSHA256: data.tls.certificate?.fingerprint256,
            serialNumber: data.tls.certificate?.serialNumber,
          },
          tlsSessionState: data.tls.tlsSessionState,
          securityNote: 'TLS verification is strictly enforced (rejectUnauthorized: true).',
          handshakeDurationMs: data.tls.durationMs,
        });

        // Populate Public Key details if present
        if (data.tls.publicKey && !data.tls.publicKey.error) {
          const pk = data.tls.publicKey;
          const keyDetailsDisplay = pk.keyDetails ? formatJson(pk.keyDetails) : 'None';
          stages.tls.pkContent.innerHTML = `
            <p class="crypto-note">This is public information from the server certificate. The corresponding private key stays secret on the server.</p>
            <div style="margin-bottom: 8px;"><strong>Public Key Type:</strong> <code>${pk.keyType}</code></div>
            <div style="margin-bottom: 4px;"><strong>Public Key Details:</strong></div>
            <pre class="details-content" style="margin-bottom: 8px;">${keyDetailsDisplay}</pre>
            <div style="margin-bottom: 4px;"><strong>Public Key (PEM):</strong></div>
            <pre class="details-content">${pk.pem}</pre>
          `;
        } else if (data.tls.publicKey?.error) {
          stages.tls.pkContent.innerHTML = `<p class="crypto-note">${data.tls.publicKey.error}</p>`;
        }
      } else {
        setStageStatus('tls', 'failed');
        stages.tls.summary.textContent = `TLS Handshake failed: ${data.tls.error || data.tls.authorizationError || 'Handshake error'}`;
        stages.tls.details.textContent = formatJson(data.tls);
      }
    } else if (data.failedStage === 'TLS') {
      setStageStatus('tls', 'failed');
      stages.tls.summary.textContent = `TLS failed: ${data.error}`;
      stages.tls.details.textContent = data.error;
    } else if (!data.tcp || data.tcp.status !== 'success') {
      setStageStatus('tls', 'waiting');
      stages.tls.summary.textContent = 'Skipped due to prior connection failure.';
    }

    // 4. HTTP Stage
    if (data.http) {
      if (data.http.status === 'success') {
        setStageStatus('http', 'success');
        stages.http.summary.textContent =
          `Request: ${data.http.method} ${data.http.path}\n` +
          `Host Header: ${data.http.hostname}\n` +
          `HTTP version used by this inspection request: HTTP/${data.http.httpVersion}`;

        stages.http.details.textContent = formatJson({
          method: data.http.method,
          path: data.http.path,
          hostHeader: data.http.hostname,
          httpVersionUsedByThisInspectionRequest: `HTTP/${data.http.httpVersion}`,
          httpVersionNote: 'This is the HTTP version used by the local Node inspector for this request. It does not claim that the destination only supports this HTTP version or that a browser would necessarily negotiate the same version.',
          freshConnection: 'agent: false, Connection: close (no connection pooling)',
        });
      } else {
        setStageStatus('http', 'failed');
        stages.http.summary.textContent = `HTTP request failed: ${data.http.error || 'Request error'}`;
        stages.http.details.textContent = formatJson(data.http);
      }
    } else if (data.failedStage === 'HTTP') {
      setStageStatus('http', 'failed');
      stages.http.summary.textContent = `HTTP failed: ${data.error}`;
      stages.http.details.textContent = data.error;
    } else if (!data.tls || data.tls.status !== 'success') {
      setStageStatus('http', 'waiting');
      stages.http.summary.textContent = 'Skipped due to prior handshake failure.';
    }

    // 5. RESPONSE Stage
    const respObj = data.decryptedResponse || data.response;
    if (respObj) {
      if (respObj.status === 'success') {
        setStageStatus('response', 'success');
        const bodyContent = respObj.body || '';
        stages.response.summary.textContent =
          `Status: ${respObj.statusCode} ${respObj.statusMessage || respObj.statusLine || 'OK'}\n` +
          `Payload Size: ${respObj.byteLength || respObj.bodySizeBytes || 0} bytes\n` +
          `Body Preview: ${bodyContent.slice(0, 120)}${bodyContent.length > 120 ? '...' : ''}`;

        stages.response.details.textContent = formatJson({
          statusCode: respObj.statusCode,
          statusLine: respObj.statusLine,
          responseHeaders: respObj.headers,
          rawBody: respObj.body,
          byteLength: respObj.byteLength || respObj.bodySizeBytes,
          truncated: respObj.truncated || respObj.bodyTruncated,
          httpResponseWaitMs: data.timings?.httpWaitMs,
          timeToFirstResponseFromInspectionStartMs: data.timings?.firstResponseFromStartMs,
        });
      } else {
        setStageStatus('response', 'failed');
        stages.response.summary.textContent = `Response retrieval failed: ${respObj.error || 'Response error'}`;
        stages.response.details.textContent = formatJson(respObj);
      }
    } else if (data.failedStage === 'RESPONSE') {
      setStageStatus('response', 'failed');
      stages.response.summary.textContent = `Response failed: ${data.error}`;
      stages.response.details.textContent = data.error;
    } else if (!data.http || data.http.status !== 'success') {
      setStageStatus('response', 'waiting');
      stages.response.summary.textContent = 'Skipped due to prior request failure.';
    }

    // Application Bytes & TLS Boundary (Phase 2 normal)
    if (data.applicationBytes) {
      boundaryElements.reqBadge.textContent = `${data.applicationBytes.request.byteLength} bytes`;
      boundaryElements.reqPlain.textContent = data.applicationBytes.request.text;
      boundaryElements.reqHex.textContent = data.applicationBytes.request.hexDump;

      boundaryElements.resBadge.textContent = `${data.applicationBytes.response.byteLength} bytes`;
      boundaryElements.resPlain.textContent = data.applicationBytes.response.text;
      boundaryElements.resHex.textContent = data.applicationBytes.response.hexDump;
    }

    // Phase 3 Deep TLS View Population
    if (isDeep && data.capturedTlsBytes && deepElements.reqPlainBadge) {
      deepElements.flowReqLine.textContent = `GET ${data.http?.path || '/'} HTTP/1.1`;

      // Plaintext Request
      deepElements.reqPlainBadge.textContent = `${data.plaintextRequest?.byteLength || 0} bytes`;
      deepElements.reqPlainText.textContent = data.plaintextRequest?.text || '';
      deepElements.reqPlainHex.textContent = data.plaintextRequest?.hexDump || '';

      // Outbound TLS Bytes
      deepElements.outboundBadge.textContent = `${data.capturedTlsBytes.outbound.totalByteCount} bytes`;
      deepElements.outboundRecords.textContent = formatJson(data.capturedTlsBytes.outbound.recordAnalysis);
      deepElements.outboundHex.textContent = data.capturedTlsBytes.outbound.hexDump;

      // Inbound TLS Bytes
      deepElements.inboundBadge.textContent = `${data.capturedTlsBytes.inbound.totalByteCount} bytes`;
      deepElements.inboundRecords.textContent = formatJson(data.capturedTlsBytes.inbound.recordAnalysis);
      deepElements.inboundHex.textContent = data.capturedTlsBytes.inbound.hexDump;

      // Decrypted Response
      deepElements.resPlainBadge.textContent = `${data.decryptedResponse?.byteLength || 0} bytes`;
      deepElements.resHeaders.textContent = `Status: ${data.decryptedResponse?.statusLine}\n\nHeaders:\n${formatJson(data.decryptedResponse?.headers)}`;
      deepElements.resPlainBody.textContent = data.decryptedResponse?.body || '';
      deepElements.resPlainHex.textContent = data.decryptedResponse?.hexDump || '';

      // TLS 1.3 Traffic Secrets
      if (data.trafficSecrets) {
        deepElements.secretsBadge.textContent = `${data.trafficSecrets.labelsCaptured.length} secrets captured`;
        deepElements.secretsContent.textContent = data.trafficSecrets.keylogRawLines.join('\n');
      }

      // Derived Symmetric Keys
      if (data.derivedKeys) {
        deepElements.derivedBadge.textContent = data.derivedKeys.supported ? `${data.derivedKeys.cipher}` : 'Unsupported';
        deepElements.derivedContent.textContent = formatJson(data.derivedKeys);
      }
    }

    // Timings
    if (data.timings) {
      timingElements.dns.textContent = data.timings.dnsLookupMs !== undefined ? `${data.timings.dnsLookupMs} ms` : '— ms';
      timingElements.tcp.textContent = data.timings.tcpConnectMs !== undefined ? `${data.timings.tcpConnectMs} ms` : '— ms';
      timingElements.tls.textContent = data.timings.tlsHandshakeMs !== undefined ? `${data.timings.tlsHandshakeMs} ms` : '— ms';
      timingElements.httpWait.textContent = data.timings.httpWaitMs !== undefined && data.timings.httpWaitMs !== null ? `${data.timings.httpWaitMs} ms` : '— ms';
      timingElements.total.textContent = data.timings.totalMs !== undefined ? `${data.timings.totalMs} ms` : '— ms';
    }

    if (data.error || data.failedStage) {
      statusBanner.className = 'status-banner error';
      statusBanner.textContent = `Inspection completed with failure at stage ${data.failedStage}: ${data.error}`;
    } else {
      statusBanner.className = 'status-banner success';
      statusBanner.textContent = `${isDeep ? 'Deep TLS inspection' : 'Real inspection'} of ${data.target} completed successfully in ${data.timings?.totalMs || '—'} ms. All stages verified.`;
    }
  } catch (err) {
    statusBanner.className = 'status-banner error';
    statusBanner.textContent = `Failed to contact local inspection server: ${err.message}`;
    for (const key of Object.keys(stages)) {
      if (stages[key].status.textContent === 'running') {
        setStageStatus(key, 'failed');
      }
    }
  } finally {
    inspectBtn.disabled = false;
    deepInspectBtn.disabled = false;
    inspectBtn.textContent = 'Run Real Inspection';
    deepInspectBtn.textContent = 'Run Deep TLS Inspection';
  }
}

inspectBtn.addEventListener('click', () => runInspection(false));
deepInspectBtn.addEventListener('click', () => runInspection(true));
targetInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    runInspection(false);
  }
});
