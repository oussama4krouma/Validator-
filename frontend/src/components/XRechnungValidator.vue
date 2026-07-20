<template>
  <div class="validator">
    <div class="card">
      <header class="card__header">
        <h2>XRechnung Validieren</h2>
      </header>

      <div class="card__body">
        <!-- Drop zone -->
        <label
          class="dropzone"
          :class="{ 'dropzone--active': isDragging, 'dropzone--filled': !!file }"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="onDrop"
        >
          <input type="file" accept=".xml" hidden @change="onFileChange" />
          <svg class="dropzone__icon" viewBox="0 0 24 24" width="34" height="34" aria-hidden="true">
            <path fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"
              stroke-linejoin="round" d="M12 16V4m0 0L8 8m4-4l4 4M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
          </svg>
          <p class="dropzone__title">
            <span class="dropzone__link">Datei auswählen</span> oder hierher ziehen
          </p>
          <p class="dropzone__hint">Dateiformat: XML</p>
        </label>

        <!-- Selected file chip -->
        <div v-if="file" class="chip">
          <span class="chip__name" :title="fileName">{{ fileName }}</span>
          <span class="chip__size">{{ fileSizeLabel }}</span>
          <button class="chip__remove" type="button" aria-label="Entfernen" @click="reset">×</button>
        </div>

        <!-- Actions -->
        <div class="actions">
          <button class="btn btn--primary" :disabled="!file || isLoading" @click="validate">
            <span v-if="isLoading" class="spinner" aria-hidden="true"></span>
            {{ isLoading ? 'Wird validiert…' : 'Validieren' }}
          </button>
          <button
            v-if="file || result || error"
            class="btn btn--ghost"
            type="button"
            :disabled="isLoading"
            @click="reset"
          >
            Zurücksetzen
          </button>
        </div>

        <!-- Inline (client-side) message -->
        <p v-if="clientMessage" class="inline-msg">{{ clientMessage }}</p>

        <!-- Result -->
        <section v-if="result" class="result">
          <div class="banner" :class="isValid ? 'banner--ok' : 'banner--bad'">
            <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
              <path v-if="isValid" fill="none" stroke="currentColor" stroke-width="2"
                stroke-linecap="round" stroke-linejoin="round" d="M20 6L9 17l-5-5" />
              <path v-else fill="none" stroke="currentColor" stroke-width="2"
                stroke-linecap="round" stroke-linejoin="round" d="M18 6L6 18M6 6l12 12" />
            </svg>
            <span class="banner__text">{{ isValid ? 'Gültig' : 'Ungültig' }}</span>
            <span class="banner__file">{{ result.fileName }}</span>
          </div>

          <!-- Parsed summary -->
          <dl v-if="report" class="meta">
            <div v-if="report.profile" class="meta__row">
              <dt>Profil</dt><dd>{{ report.profile }}</dd>
            </div>
            <div class="meta__row">
              <dt>Regeln</dt>
              <dd>{{ report.fired || '–' }} geprüft<span v-if="report.failed"> · {{ report.failed }} fehlgeschlagen</span></dd>
            </div>
            <div v-if="report.duration" class="meta__row">
              <dt>Dauer</dt><dd>{{ report.duration }} ms</dd>
            </div>
            <div v-if="report.validatorVersion" class="meta__row">
              <dt>Validator</dt><dd>Mustang {{ report.validatorVersion }}</dd>
            </div>
          </dl>

          <!-- Messages -->
          <ul v-if="report && report.errors.length" class="msg-list msg-list--error">
            <li v-for="(m, i) in report.errors" :key="'e' + i">{{ m }}</li>
          </ul>
          <ul v-if="report && report.warnings.length" class="msg-list msg-list--warn">
            <li v-for="(m, i) in report.warnings" :key="'w' + i">{{ m }}</li>
          </ul>
          <ul v-if="report && report.notices.length" class="msg-list msg-list--notice">
            <li v-for="(m, i) in report.notices" :key="'n' + i">{{ m }}</li>
          </ul>

          <!-- Raw report -->
          <details class="raw">
            <summary>Vollständiger Bericht (XML)</summary>
            <pre>{{ result.validationResult }}</pre>
          </details>
        </section>

        <!-- Server / network error -->
        <div v-if="error" class="error-box">{{ error }}</div>
      </div>
    </div>

    <p class="validator-info">
      Im Rahmen unseres Projekts wird der Mustangproject Validator (Version 2.24.0) zur Validierung
      elektronischer Rechnungen eingesetzt. Die Bibliothek ist eine Open-Source-Java-Lösung, die
      verschiedene Rechnungsformate wie ZUGFeRD 2/Factur-X, ZUGFeRD 1 und XRechnung unterstützt.
    </p>
  </div>
</template>

<script>
import axios from 'axios';

const API_URL = 'http://localhost:8080/upload-xml';

export default {
  name: 'XRechnungValidator',

  data() {
    return {
      file: null,
      fileName: '',
      result: null,
      status: '',
      error: null,
      clientMessage: '',
      isDragging: false,
      isLoading: false,
    };
  },

  computed: {
    isValid() {
      return this.status === 'VALID';
    },
    fileSizeLabel() {
      if (!this.file) return '';
      const kb = this.file.size / 1024;
      return kb < 1024 ? `${kb.toFixed(1)} KB` : `${(kb / 1024).toFixed(1)} MB`;
    },
    // Parses Mustang's XML report into a friendly summary. Returns null if it can't be parsed.
    report() {
      const xml = this.result && this.result.validationResult;
      if (!xml) return null;
      try {
        const doc = new DOMParser().parseFromString(xml, 'application/xml');
        if (doc.querySelector('parsererror')) return null;

        const textOf = (sel) => {
          const el = doc.querySelector(sel);
          return el ? el.textContent.trim() : '';
        };
        const listOf = (sel) =>
          [...doc.querySelectorAll(sel)].map((el) => el.textContent.trim()).filter(Boolean);

        return {
          profile: textOf('info > profile') || textOf('profile'),
          validatorVersion: doc.querySelector('validator')?.getAttribute('version') || '',
          fired: textOf('rules > fired') || textOf('fired'),
          failed: textOf('rules > failed') || textOf('failed'),
          duration: textOf('duration'),
          errors: listOf('error'),
          warnings: listOf('warning'),
          notices: listOf('notice'),
        };
      } catch {
        return null;
      }
    },
  },

  methods: {
    onFileChange(event) {
      const picked = event.target.files[0];
      // Reset the input so selecting the same file again still fires @change.
      event.target.value = '';
      this.setFile(picked);
    },

    onDrop(event) {
      this.isDragging = false;
      this.setFile(event.dataTransfer.files[0]);
    },

    setFile(picked) {
      if (!picked) return;
      this.clientMessage = '';
      if (!picked.name.toLowerCase().endsWith('.xml')) {
        this.clientMessage = 'Bitte eine XML-Datei auswählen.';
        return;
      }
      this.file = picked;
      this.fileName = picked.name;
      this.result = null;
      this.status = '';
      this.error = null;
    },

    async validate() {
      if (!this.file) return;

      this.isLoading = true;
      this.result = null;
      this.status = '';
      this.error = null;
      this.clientMessage = '';

      const formData = new FormData();
      formData.append('file', this.file);

      try {
        const { data } = await axios.post(API_URL, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
        this.result = {
          fileName: data.fileName,
          validationResult: data.validationResult,
        };
        this.status = data.status || 'UNKNOWN';
      } catch (err) {
        this.error =
          err.response?.data?.error || 'Fehler beim Hochladen oder Validieren der Datei!';
        this.result = null;
        this.status = '';
      } finally {
        this.isLoading = false;
      }
    },

    reset() {
      this.file = null;
      this.fileName = '';
      this.result = null;
      this.status = '';
      this.error = null;
      this.clientMessage = '';
      this.isDragging = false;
    },
  },
};
</script>

<style scoped>
.validator {
  --accent: #cdd816;
  --accent-strong: #a9b30f;
  --ink: #1a1a1a;
  --muted: #6b7280;
  --line: #e5e7eb;
  --ok: #16a34a;
  --ok-bg: #ecfdf3;
  --bad: #dc2626;
  --bad-bg: #fef2f2;

  max-width: 640px;
  margin: 0 auto;
  font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  color: var(--ink);
}

.card {
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.card__header {
  background: var(--accent);
  padding: 16px 22px;
}
.card__header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}
.card__body {
  padding: 22px;
}

/* Drop zone */
.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 38px 20px;
  border: 2px dashed #cbd5e1;
  border-radius: 12px;
  color: var(--muted);
  cursor: pointer;
  text-align: center;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}
.dropzone:hover {
  border-color: var(--accent-strong);
}
.dropzone--active {
  border-color: var(--accent-strong);
  background: #fbfce4;
  color: var(--ink);
}
.dropzone--filled {
  border-style: solid;
  border-color: var(--line);
}
.dropzone__icon {
  color: var(--accent-strong);
}
.dropzone__title {
  margin: 4px 0 0;
  font-size: 15px;
  color: var(--ink);
}
.dropzone__link {
  color: var(--accent-strong);
  font-weight: 600;
  text-decoration: underline;
}
.dropzone__hint {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
}

/* File chip */
.chip {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 10px 12px;
  background: #f9fafb;
  border: 1px solid var(--line);
  border-radius: 10px;
  font-size: 14px;
}
.chip__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}
.chip__size {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}
.chip__remove {
  border: none;
  background: none;
  font-size: 20px;
  line-height: 1;
  color: var(--muted);
  cursor: pointer;
  padding: 0 4px;
}
.chip__remove:hover {
  color: var(--bad);
}

/* Actions */
.actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
.btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 11px 16px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 9px;
  cursor: pointer;
  transition: background 0.15s, opacity 0.15s;
}
.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.btn--primary {
  background: var(--ink);
  color: #fff;
  border: 1px solid var(--ink);
}
.btn--primary:not(:disabled):hover {
  background: #000;
}
.btn--ghost {
  flex: 0 0 auto;
  background: #fff;
  color: var(--ink);
  border: 1px solid var(--line);
}
.btn--ghost:not(:disabled):hover {
  background: #f9fafb;
}

.spinner {
  width: 15px;
  height: 15px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.inline-msg {
  margin: 12px 0 0;
  font-size: 14px;
  color: var(--bad);
}

/* Result */
.result {
  margin-top: 20px;
}
.banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  font-weight: 600;
}
.banner--ok {
  background: var(--ok-bg);
  color: var(--ok);
}
.banner--bad {
  background: var(--bad-bg);
  color: var(--bad);
}
.banner__text {
  font-size: 16px;
}
.banner__file {
  margin-left: auto;
  font-weight: 400;
  font-size: 13px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 45%;
}

.meta {
  margin: 14px 0 0;
  display: grid;
  gap: 6px;
}
.meta__row {
  display: flex;
  gap: 10px;
  font-size: 14px;
}
.meta__row dt {
  flex: 0 0 90px;
  color: var(--muted);
}
.meta__row dd {
  margin: 0;
  word-break: break-word;
}

.msg-list {
  margin: 14px 0 0;
  padding: 12px 14px 12px 30px;
  border-radius: 10px;
  font-size: 13.5px;
  line-height: 1.5;
}
.msg-list li {
  margin: 2px 0;
}
.msg-list--error {
  background: var(--bad-bg);
  color: #991b1b;
}
.msg-list--warn {
  background: #fffbeb;
  color: #92400e;
}
.msg-list--notice {
  background: #f9fafb;
  color: var(--muted);
}

.raw {
  margin-top: 14px;
}
.raw summary {
  cursor: pointer;
  font-size: 13px;
  color: var(--muted);
  user-select: none;
}
.raw pre {
  margin: 10px 0 0;
  padding: 12px;
  max-height: 320px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.error-box {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #fecaca;
  background: var(--bad-bg);
  border-radius: 10px;
  color: #991b1b;
  font-size: 14px;
}

.validator-info {
  margin: 18px 2px 0;
  padding: 12px 16px;
  background: #fbfce4;
  border-left: 4px solid var(--accent);
  border-radius: 8px;
  font-size: 13.5px;
  line-height: 1.55;
  color: #374151;
}
</style>
