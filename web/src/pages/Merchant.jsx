import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'

const MAX_SECONDS = 25

export default function Merchant() {
  const [recording, setRecording] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const [blob, setBlob] = useState(null)
  const [language, setLanguage] = useState('unknown')
  const [status, setStatus] = useState('')
  const [transcript, setTranscript] = useState('')
  const [items, setItems] = useState([])
  const [listed, setListed] = useState(false)
  const recorderRef = useRef(null)
  const timerRef = useRef(null)
  const chunksRef = useRef([])

  async function start() {
    let stream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch (e) {
      setStatus('Microphone blocked: ' + e.message)
      return
    }
    chunksRef.current = []
    setBlob(null); setTranscript(''); setItems([]); setListed(false); setStatus('')
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
      setTranscript(data.transcript)
      setStatus('')
      // Dummy parsing: real parsing comes later. Shows an editable confirm list.
      setItems([
        { name: 'Milk 500 ml', qty: 6, price: 22 },
        { name: 'Bread', qty: 4, price: 30 },
      ])
    } catch (e) {
      setStatus('Request failed: ' + e.message)
    }
  }

  function update(i, key, value) {
    setItems(items.map((it, idx) => (idx === i ? { ...it, [key]: value } : it)))
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
          <div className="muted">Say the items, quantity and your price. Example: "doodh 6 packet 22, bread 4 loaf 30"</div>
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
        </div>

        {transcript && (
          <div className="card">
            <div className="muted">We heard</div>
            <div className="transcript">{transcript}</div>
            <div className="muted" style={{ marginBottom: 8 }}>
              Check each item and price. Sample parsing for now: the real reader comes next.
            </div>
            <div className="items">
              {items.map((it, i) => (
                <div className="itemrow" key={i}>
                  <input value={it.name} onChange={(e) => update(i, 'name', e.target.value)} />
                  <input type="number" value={it.qty} onChange={(e) => update(i, 'qty', e.target.value)} />
                  <input type="number" value={it.price} onChange={(e) => update(i, 'price', e.target.value)} />
                </div>
              ))}
              <div className="muted">Item · Quantity · Price (₹)</div>
            </div>
            {!listed ? (
              <button className="btn" onClick={() => setListed(true)}>List now</button>
            ) : (
              <>
                <div className="badge ok" style={{ marginTop: 12 }}>Live · offer ends at closing time</div>
                <button className="btn alt" onClick={() => { setListed(false); setItems([]); setTranscript('') }}>
                  Mark all sold out
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
