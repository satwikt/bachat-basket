import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'

const MAX_SECONDS = 25
const SHOP = '/api/shops/1/listings' // no auth in MVP; shop 1 is hard-coded (spec D5)

let nextKey = 1

function newRow(fields = {}) {
  return {
    key: nextKey++,
    rawPhrase: '',
    itemName: '',
    itemKey: null,
    quantity: '',
    unit: '',
    offerPrice: '',
    originalPrice: '',
    serverWarnings: [], // NO_UNIT / UNKNOWN_ITEM from the parser; cleared when the merchant fixes that field
    ...fields,
  }
}

function fromServer(line) {
  return newRow({
    rawPhrase: line.rawPhrase ?? '',
    itemName: line.itemName ?? '',
    itemKey: line.itemKey ?? null,
    quantity: line.quantity ?? '',
    unit: line.unit ?? '',
    offerPrice: line.offerPrice ?? '',
    originalPrice: line.originalPrice ?? '',
    serverWarnings: (line.issues || []).filter((i) => i === 'NO_UNIT' || i === 'UNKNOWN_ITEM'),
  })
}

function toNum(v) {
  if (v === '' || v === null || v === undefined) return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

/** Browser-side checks (spec 8.1). The server re-checks everything on save and is the final gate. */
function flagsOf(r) {
  const flags = []
  const q = toNum(r.quantity)
  const offer = toNum(r.offerPrice)
  const orig = toNum(r.originalPrice)

  if (!r.itemName.trim() || r.quantity === '' || r.offerPrice === '') {
    flags.push({ text: 'Not understood', blocking: true })
  }
  if (q !== null && q <= 0) flags.push({ text: 'Quantity must be above 0', blocking: true })
  if (offer !== null && offer <= 0) flags.push({ text: 'Offer price missing', blocking: true })
  if (orig === null || orig <= 0) {
    flags.push({ text: 'Original price missing', blocking: true })
  } else if (offer !== null && offer > 0 && offer >= orig) {
    flags.push({ text: 'Price above original', blocking: true })
  }
  if (!r.unit.trim() || r.serverWarnings.includes('NO_UNIT')) {
    flags.push({ text: 'Unit missing (will be pc)', blocking: false })
  }
  if (r.serverWarnings.includes('UNKNOWN_ITEM')) {
    flags.push({ text: 'Item not in our list', blocking: false })
  }
  return flags
}

function percentOff(r) {
  const orig = toNum(r.originalPrice)
  const offer = toNum(r.offerPrice)
  if (orig === null || orig <= 0 || offer === null || offer < 0) return '—'
  return Math.round(((orig - offer) * 100) / orig) + '%'
}

export default function Merchant() {
  const [recording, setRecording] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const [blob, setBlob] = useState(null)
  const [language, setLanguage] = useState('unknown')
  const [status, setStatus] = useState('')
  const [transcript, setTranscript] = useState('')
  const [typed, setTyped] = useState('')
  const [parseError, setParseError] = useState('')
  const [parsing, setParsing] = useState(false)
  const [rows, setRows] = useState([])
  const [listed, setListed] = useState(false)
  const [listing, setListing] = useState(false)
  const [notice, setNotice] = useState('')
  const recorderRef = useRef(null)
  const timerRef = useRef(null)
  const chunksRef = useRef([])

  function resetTable() {
    setRows([])
    setListed(false)
    setParseError('')
    setTranscript('')
    setTyped('')
  }

  async function start() {
    let stream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch (e) {
      setStatus('Microphone blocked: ' + e.message)
      return
    }
    chunksRef.current = []
    setBlob(null)
    setStatus('')
    setNotice('')
    resetTable()
    const rec = new MediaRecorder(stream)
    recorderRef.current = rec
    rec.ondataavailable = (e) => { if (e.data.size) chunksRef.current.push(e.data) }
    rec.onstop = () => {
      clearInterval(timerRef.current)
      stream.getTracks().forEach((t) => t.stop())
      setBlob(new Blob(chunksRef.current, { type: rec.mimeType || 'audio/webm' }))
      setRecording(false)
    }
    rec.start()
    setRecording(true)
    setSeconds(0)
    const t0 = Date.now()
    timerRef.current = setInterval(() => {
      const s = Math.floor((Date.now() - t0) / 1000)
      setSeconds(s)
      if (s >= MAX_SECONDS) rec.stop()
    }, 250)
  }

  function stop() { recorderRef.current?.stop() }

  /** Sends text to the parser and shows the lines it returns. Writes nothing on the server. */
  async function parseText(text) {
    const clean = text.trim()
    if (!clean) { setParseError('Type or say the items first'); return }
    setParsing(true)
    setParseError('')
    setTranscript(clean)
    try {
      const res = await fetch(SHOP + '/parse', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ transcript: clean }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) {
        setParseError(data.error || 'Could not read the items')
        setRows([])
        return
      }
      setRows(data.lines.map(fromServer))
      setListed(false)
      setNotice('')
    } catch (e) {
      setParseError('Request failed: ' + e.message)
    } finally {
      setParsing(false)
    }
  }

  async function send() {
    if (!blob) return
    setStatus('Reading your voice...')
    const ext = blob.type.includes('mp4') ? 'mp4' : blob.type.includes('ogg') ? 'ogg' : 'webm'
    const fd = new FormData()
    fd.append('audio', blob, 'recording.' + ext)
    fd.append('language', language)
    try {
      const res = await fetch('/api/speech/transcribe', { method: 'POST', body: fd })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) { setStatus('Failed: ' + (data.error || res.status)); return }
      setStatus('')
      setTyped(data.transcript)
      await parseText(data.transcript)
    } catch (e) {
      setStatus('Request failed: ' + e.message)
    }
  }

  function updateRow(key, patch) {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)))
  }

  function editItem(r, value) {
    // a changed item name is looked up again on save; the old key and "not in our list" warning no longer apply
    updateRow(r.key, {
      itemName: value,
      itemKey: null,
      serverWarnings: r.serverWarnings.filter((w) => w !== 'UNKNOWN_ITEM'),
    })
  }

  function editUnit(r, value) {
    updateRow(r.key, {
      unit: value,
      serverWarnings: r.serverWarnings.filter((w) => w !== 'NO_UNIT'),
    })
  }

  function deleteRow(key) {
    setRows((prev) => prev.filter((r) => r.key !== key))
  }

  const anyBlocking = rows.some((r) => flagsOf(r).some((f) => f.blocking))
  const canList = rows.length > 0 && !anyBlocking && !listed && !listing

  async function listNow() {
    setListing(true)
    setNotice('')
    const body = {
      lines: rows.map((r) => ({
        rawPhrase: r.rawPhrase,
        itemKey: r.itemKey,
        itemName: r.itemName.trim(),
        quantity: toNum(r.quantity),
        unit: r.unit.trim() || 'pc',
        offerPrice: toNum(r.offerPrice),
        originalPrice: toNum(r.originalPrice),
        sellToday: false,
      })),
    }
    try {
      const res = await fetch(SHOP, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) {
        setNotice(data.error || 'Could not list the items')
        return
      }
      setListed(true)
      setNotice('Live. Offers end at closing time.')
    } catch (e) {
      setNotice('Request failed: ' + e.message)
    } finally {
      setListing(false)
    }
  }

  async function markAllSoldOut() {
    setNotice('')
    try {
      const res = await fetch(SHOP + '/sold-out', { method: 'POST' })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) {
        setNotice(data.error || 'Could not mark items sold out')
        return
      }
      resetTable()
      setNotice(`Marked ${data.updated} offer(s) sold out.`)
    } catch (e) {
      setNotice('Request failed: ' + e.message)
    }
  }

  return (
    <div className="app">
      <div className="topbar">
        <h1>Shopkeeper · Add items</h1>
        <Link to="/">Home</Link>
      </div>
      <div className="pad">
        <div className="card">
          <h3>Sharma General Store</h3>
          <div className="muted">
            Say each item with its quantity, original price and discounted price, in that order.
            Example: "doodh 6 packet, original price 30 discounted price 23"
          </div>
          <select value={language} onChange={(e) => setLanguage(e.target.value)} style={{ marginTop: 12 }}>
            <option value="unknown">Auto-detect language</option>
            <option value="hi-IN">Hindi</option>
            <option value="mr-IN">Marathi</option>
            <option value="gu-IN">Gujarati</option>
            <option value="en-IN">English (India)</option>
          </select>
          {!recording ? (
            <button className="btn" onClick={start}>{blob ? 'Record again' : 'Start speaking'}</button>
          ) : (
            <button className="btn rec" onClick={stop}>Stop ({seconds}s)</button>
          )}
          {blob && !recording && (
            <button className="btn amber" onClick={send}>Read my items</button>
          )}
          {status && <div className="muted" style={{ marginTop: 10 }}>{status}</div>}
          {notice && <div className="muted" style={{ marginTop: 10 }}>{notice}</div>}
        </div>

        {(transcript || parseError) && (
          <div className="card">
            {transcript && (
              <>
                <div className="muted">We heard</div>
                <div className="transcript">{transcript}</div>
              </>
            )}
            {parseError && <div className="badge bad" style={{ marginBottom: 8 }}>{parseError}</div>}
            {transcript && (
              <>
                <div className="muted">Wrong? Type the items instead:</div>
                <textarea
                  rows={3}
                  value={typed}
                  onChange={(e) => setTyped(e.target.value)}
                  style={{ width: '100%', marginTop: 6, fontSize: 16, padding: 10, borderRadius: 10, border: '1px solid var(--line)' }}
                />
                <button className="btn alt" disabled={parsing} onClick={() => parseText(typed)}>
                  {parsing ? 'Reading...' : 'Read these items'}
                </button>
              </>
            )}
          </div>
        )}

        {rows.length > 0 && (
          <div className="card">
            <div className="muted" style={{ marginBottom: 8 }}>
              Check each item. Fix anything marked red before you list.
            </div>
            {rows.map((r) => {
              const flags = flagsOf(r)
              const blocking = flags.some((f) => f.blocking)
              return (
                <div className={'lrow' + (blocking ? ' bad' : '')} key={r.key}>
                  <div className="lbl">Heard: {r.rawPhrase || '—'}</div>
                  <div className="lgrid1">
                    <div>
                      <div className="lbl">Item</div>
                      <input value={r.itemName} disabled={listed} onChange={(e) => editItem(r, e.target.value)} />
                    </div>
                    <div>
                      <div className="lbl">Qty</div>
                      <input type="number" min="0" value={r.quantity} disabled={listed}
                        onChange={(e) => updateRow(r.key, { quantity: e.target.value })} />
                    </div>
                    <div>
                      <div className="lbl">Unit</div>
                      <input value={r.unit} disabled={listed} onChange={(e) => editUnit(r, e.target.value)} />
                    </div>
                  </div>
                  <div className="lgrid2">
                    <div>
                      <div className="lbl">Offer ₹ (discounted)</div>
                      <input type="number" min="0" step="0.01" value={r.offerPrice} disabled={listed}
                        onChange={(e) => updateRow(r.key, { offerPrice: e.target.value })} />
                    </div>
                    <div>
                      <div className="lbl">Original ₹</div>
                      <input type="number" min="0" step="0.01" value={r.originalPrice} disabled={listed}
                        onChange={(e) => updateRow(r.key, { originalPrice: e.target.value })} />
                    </div>
                    <div>
                      <div className="lbl">% off</div>
                      <div className="pct">{percentOff(r)}</div>
                    </div>
                  </div>
                  <div className="foot">
                    {flags.map((f) => (
                      <span key={f.text} className={'badge ' + (f.blocking ? 'bad' : 'warn')}>{f.text}</span>
                    ))}
                    {!listed && (
                      <button className="btn small alt" onClick={() => deleteRow(r.key)} aria-label="Delete row">✕</button>
                    )}
                  </div>
                </div>
              )
            })}
            {!listed && (
              <button className="btn small alt" onClick={() => setRows((p) => [...p, newRow()])}>+ Add row</button>
            )}

            {!listed ? (
              <button className="btn" disabled={!canList} onClick={listNow}>
                {listing ? 'Listing...' : 'List now'}
              </button>
            ) : (
              <>
                <div className="badge ok" style={{ marginTop: 12 }}>Live · offers end at closing time</div>
                <button className="btn alt" onClick={markAllSoldOut}>Mark all sold out</button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
