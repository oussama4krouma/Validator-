<template>
  <div class="validator">
    <div class="card">
      <!-- Brand header -->
      <header class="brand">
        <img class="brand__logo" :src="logoUrl" alt="COUNT+CARE" />
        <div class="brand__text">
          <h1>XRechnung Validator</h1>
          <p>Elektronische Rechnungen pr&uuml;fen &ndash; XRechnung &middot; ZUGFeRD &middot; Factur-X</p>
        </div>
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
            <span class="dropzone__link">Datei ausw&auml;hlen</span> oder hierher ziehen
          </p>
          <p class="dropzone__hint">Dateiformat: XML</p>
        </label>

        <!-- Selected file chip -->
        <div v-if="file" class="chip">
          <span class="chip__name" :title="fileName">{{ fileName }}</span>
          <span class="chip__size">{{ fileSizeLabel }}</span>
          <button class="chip__remove" type="button" aria-label="Entfernen" @click="reset">&times;</button>
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
            Zur&uuml;cksetzen
          </button>
        </div>

        <!-- Inline (client-side) message -->
        <p v-if="clientMessage" class="inline-msg">{{ clientMessage }}</p>

        <!-- Result -->
        <section v-if="result" class="result">

          <!-- ===================== BEWERTUNG ===================== -->
          <div v-if="bewertung" class="bewertung" :class="'bewertung--' + bewertung.tone">
            <div class="bewertung__head">
              <svg viewBox="0 0 24 24" width="26" height="26" aria-hidden="true">
                <path v-if="bewertung.tone === 'ok'" fill="none" stroke="currentColor" stroke-width="2"
                  stroke-linecap="round" stroke-linejoin="round" d="M20 6L9 17l-5-5" />
                <g v-else-if="bewertung.tone === 'warn'" fill="none" stroke="currentColor" stroke-width="2"
                  stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 9v4" /><path d="M12 17h.01" />
                  <path d="M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
                </g>
                <path v-else fill="none" stroke="currentColor" stroke-width="2"
                  stroke-linecap="round" stroke-linejoin="round" d="M18 6L6 18M6 6l12 12" />
              </svg>
              <div>
                <p class="bewertung__verdict">{{ bewertung.title }}</p>
                <p class="bewertung__text">{{ bewertung.text }}</p>
              </div>
            </div>

            <ul v-if="bewertung.items.length" class="findings">
              <li v-for="(f, i) in bewertung.items" :key="i" class="finding">
                <span v-if="f.id" class="finding__id">{{ f.id }}</span>
                <span class="finding__text">{{ f.text }}</span>
              </li>
            </ul>
          </div>
          <!-- ===================================================== -->

          <!-- Facts -->
          <dl v-if="report" class="meta">
            <div class="meta__row">
              <dt>Datei</dt><dd>{{ result.fileName }}</dd>
            </div>
            <div v-if="report.profile" class="meta__row">
              <dt>Profil</dt><dd>{{ report.profile }}</dd>
            </div>
            <div class="meta__row">
              <dt>Regeln</dt>
              <dd>{{ report.fired || '–' }} gepr&uuml;ft<span v-if="report.failed"> &middot; {{ report.failed }} beanstandet</span></dd>
            </div>
            <div v-if="report.duration" class="meta__row">
              <dt>Dauer</dt><dd>{{ report.duration }} ms</dd>
            </div>
            <div v-if="report.validatorVersion" class="meta__row">
              <dt>Validator</dt><dd>Mustang {{ report.validatorVersion }}</dd>
            </div>
          </dl>

          <!-- Raw report -->
          <details class="raw">
            <summary>Vollst&auml;ndiger Bericht (XML)</summary>
            <pre>{{ result.validationResult }}</pre>
          </details>
        </section>

        <!-- Server / network error -->
        <div v-if="error" class="error-box">{{ error }}</div>
      </div>
    </div>

    <p class="validator-info">
      Zur Validierung wird der Mustangproject Validator (Version 2.24.0) eingesetzt &ndash; eine
      Open-Source-Java-Bibliothek, die Rechnungsformate wie XRechnung, ZUGFeRD 2/Factur-X und
      ZUGFeRD 1 unterst&uuml;tzt.
    </p>
  </div>
</template>

<script>
import axios from 'axios';
import logoUrl from '../assets/logo.jpg';

// Relative path on purpose: works locally via the Vite proxy and in the DMZ via nginx.
const API_URL = '/api/upload-xml';

export default {
  name: 'XRechnungValidator',

  data() {
    return {
      logoUrl,
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

    // Parses Mustang's XML report into a friendly summary. Returns null if unparseable.
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
          notices: listOf('notice'),
        };
      } catch {
        return null;
      }
    },

    /**
     * Plain-language verdict ("Bewertung") with three cases:
     *  1. valid, no findings   -> file is fine
     *  2. valid, with findings -> file is usable, but has remarks worth reviewing
     *  3. invalid              -> file must be reworked; affected places listed
     */
    bewertung() {
      if (!this.result) return null;

      const raw = this.report ? [...this.report.errors, ...this.report.notices] : [];
      const items = raw.map(this.parseFinding);

      if (this.isValid && items.length === 0) {
        return {
          tone: 'ok',
          title: 'Die Datei ist in Ordnung.',
          text: 'Die Rechnung ist konform und kann so verwendet werden.',
          items: [],
        };
      }

      if (this.isValid) {
        return {
          tone: 'warn',
          title: 'Die Datei ist g\u00fcltig \u2013 mit Anmerkungen.',
          text: 'Die Rechnung kann verwendet werden. Folgende Stellen sollten aber gepr\u00fcft werden:',
          items,
        };
      }

      return {
        tone: 'bad',
        title: 'Die Datei muss \u00fcberarbeitet werden.',
        text: 'Die Rechnung ist nicht konform. Folgende Stellen sind zu korrigieren:',
        items,
      };
    },
  },

  methods: {
    /**
     * Turns a raw Mustang message like
     *   "[UBL-DT-26]-EncodingCode attribute should not be present [ID UBL-DT-26] from /xslt/...xslt)"
     * into { id: 'UBL-DT-26', text: 'EncodingCode attribute should not be present' }.
     */
    parseFinding(msg) {
      let text = msg;
      let id = '';

      const lead = text.match(/^\[([^\]]+)\][-\s]*/);
      if (lead) {
        id = lead[1];
        text = text.slice(lead[0].length);
      }

      // Drop redundant "[ID ...]" and the technical "from /xslt/..." tail.
      text = text.replace(/\[ID [^\]]+\]/g, '');
      text = text.replace(/\bfrom \/?xslt\/[^\s)]*\)?/gi, '');
      text = text.replace(/\s{2,}/g, ' ').trim().replace(/[)\s]+$/, '');

      return { id, text: text || msg };
    },

    onFileChange(event) {
      const picked = event.target.files[0];
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
        this.clientMessage = 'Bitte eine XML-Datei ausw\u00e4hlen.';
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
  /* COUNT+CARE palette: grey wordmark, daisy-yellow accent */
  --ink: #3c3c3b;
  --muted: #8a8c8e;
  --line: #e4e6e6;
  --paper: #ffffff;
  --accent: #f0b41c;        /* daisy centre */
  --accent-soft: #fdf4dd;
  --ok: #2e7d46;
  --ok-bg: #ecf6ef;
  --warn: #9a6700;
  --warn-bg: #fdf6e3;
  --bad: #b3261e;
  --bad-bg: #fdecea;

  max-width: 680px;
  margin: 0 auto;
  font-family: 'Segoe UI', system-ui, -apple-system, Roboto, sans-serif;
  color: var(--ink);
}

.card {
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--paper);
  box-shadow: 0 10px 30px rgba(60, 60, 59, 0.08);
  overflow: hidden;
}

/* Brand header */
.brand {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px 26px;
  border-bottom: 3px solid var(--accent);
}
.brand__logo {
  height: 44px;
  width: auto;
  flex: 0 0 auto;
}
.brand__text h1 {
  margin: 0;
  font-size: 19px;
  font-weight: 650;
  letter-spacing: 0.01em;
}
.brand__text p {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--muted);
}

.card__body {
  padding: 24px 26px 26px;
}

/* Drop zone */
.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 38px 20px;
  border: 2px dashed #cfd2d2;
  border-radius: 12px;
  color: var(--muted);
  cursor: pointer;
  text-align: center;
  transition: border-color 0.15s, background 0.15s;
}
.dropzone:hover {
  border-color: var(--accent);
}
.dropzone--active {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--ink);
}
.dropzone--filled {
  border-style: solid;
  border-color: var(--line);
}
.dropzone__icon { color: var(--accent); }
.dropzone__title {
  margin: 4px 0 0;
  font-size: 15px;
  color: var(--ink);
}
.dropzone__link {
  color: #a97e0d;
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
  background: #f8f9f9;
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
.chip__size { color: var(--muted); font-size: 12px; white-space: nowrap; }
.chip__remove {
  border: none;
  background: none;
  font-size: 20px;
  line-height: 1;
  color: var(--muted);
  cursor: pointer;
  padding: 0 4px;
}
.chip__remove:hover { color: var(--bad); }

/* Actions */
.actions { display: flex; gap: 10px; margin-top: 16px; }
.btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s, opacity 0.15s;
}
.btn:disabled { opacity: 0.55; cursor: not-allowed; }
.btn--primary {
  background: var(--ink);
  color: #fff;
  border: 1px solid var(--ink);
}
.btn--primary:not(:disabled):hover { background: #262626; }
.btn--ghost {
  flex: 0 0 auto;
  background: #fff;
  color: var(--ink);
  border: 1px solid var(--line);
}
.btn--ghost:not(:disabled):hover { background: #f8f9f9; }

.spinner {
  width: 15px;
  height: 15px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.inline-msg { margin: 12px 0 0; font-size: 14px; color: var(--bad); }

/* Result */
.result { margin-top: 20px; }

/* Bewertung — the plain-language verdict */
.bewertung {
  border-radius: 12px;
  padding: 16px 18px;
  border: 1px solid;
}
.bewertung--ok   { background: var(--ok-bg);   border-color: #bfe0ca; color: var(--ok); }
.bewertung--warn { background: var(--warn-bg); border-color: #ecd9a0; color: var(--warn); }
.bewertung--bad  { background: var(--bad-bg);  border-color: #f2c0bc; color: var(--bad); }

.bewertung__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.bewertung__verdict {
  margin: 0;
  font-size: 16.5px;
  font-weight: 700;
}
.bewertung__text {
  margin: 3px 0 0;
  font-size: 14px;
  color: var(--ink);
}

.findings {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
  display: grid;
  gap: 8px;
}
.finding {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 13.5px;
  line-height: 1.5;
  color: var(--ink);
}
.finding__id {
  flex: 0 0 auto;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.02em;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--ink);
  color: #fff;
  margin-top: 1px;
  white-space: nowrap;
}
.finding__text { word-break: break-word; }

/* Facts */
.meta { margin: 16px 0 0; display: grid; gap: 6px; }
.meta__row { display: flex; gap: 10px; font-size: 14px; }
.meta__row dt { flex: 0 0 90px; color: var(--muted); }
.meta__row dd { margin: 0; word-break: break-word; }

/* Raw report */
.raw { margin-top: 16px; }
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
  background: #2b2b2a;
  color: #e8e8e6;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.error-box {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #f2c0bc;
  background: var(--bad-bg);
  border-radius: 10px;
  color: var(--bad);
  font-size: 14px;
}

.validator-info {
  margin: 18px 2px 0;
  padding: 12px 16px;
  background: var(--accent-soft);
  border-left: 4px solid var(--accent);
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.55;
  color: #555;
}
</style>
