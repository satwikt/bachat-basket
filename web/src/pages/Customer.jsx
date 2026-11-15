import { useEffect, useRef, useState } from 'react'

const CUSTOMERS = '/api/customers'
const ID_KEY = 'bb_customer_id'
const TABS = [
  { key: 'home', label: 'Home' },
  { key: 'nearby', label: 'Nearby' },
  { key: 'follow', label: 'Following' },
  { key: 'deal', label: 'Find a deal' },
]

// ---- small helpers ----

function readStoredId() {
  try {
    const v = window.localStorage.getItem(ID_KEY)
    return v ? Number(v) : null
  } catch {
    return null // storage blocked: work for this visit only
  }
}

function storeId(id) {
  try {
    window.localStorage.setItem(ID_KEY, String(id))
  } catch {
    /* ignore: the id lives in memory for this visit */
  }
}

function clearStoredId() {
  try {
    window.localStorage.removeItem(ID_KEY)
  } catch {
    /* ignore */
  }
}

async function call(path, options) {
  const res = await fetch(path, options)
  const data = await res.json().catch(() => ({}))
  return { ok: res.ok, status: res.status, data }
}

function distanceKm(a, b) {
  const R = 6371
  const rad = (d) => (d * Math.PI) / 180
  const dLat = rad(b.lat - a.lat)
  const dLng = rad(b.lng - a.lng)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}

function formatKm(km) {
  return km < 1 ? `${Math.round(km * 1000)} m` : `${km.toFixed(1)} km`
}

function unitLabel(quantity, unit) {
  return unit === 'packet' && Number(quantity) > 1 ? 'packets' : unit
}

function endsLabel(iso) {
  return new Date(iso).toLocaleTimeString('en-IN', {
    hour: 'numeric',
    minute: '2-digit',
    timeZone: 'Asia/Kolkata',
  })
}

/** "23:00:00" from the API -> "23:00" for display. */
function hhmm(time) {
  return time ? String(time).slice(0, 5) : ''
}

function directionsUrl(shop) {
  if (shop.latitude == null || shop.longitude == null) return null
  return `https://www.google.com/maps/search/?api=1&query=${shop.latitude},${shop.longitude}`
}

// ---- page ----

export default function Customer() {
  const [tab, setTab] = useState('home')
  const [customerId, setCustomerId] = useState(null)
  const [shops, setShops] = useState([])
  const [followed, setFollowed] = useState(() => new Set())
  const [offers, setOffers] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [location, setLocation] = useState(null)
  const started = useRef(false)
  const locationAsked = useRef(false)

  async function createCustomer() {
    const r = await call(CUSTOMERS, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: null }),
    })
    if (!r.ok) throw new Error('could not start a session')
    return r.data.customerId
  }

  /** Loads shops, follows and offers for one customer. Returns null when the id is unknown (stale). */
  async function loadFor(id) {
    const [s, sub, off] = await Promise.all([
      call('/api/shops'),
      call(`${CUSTOMERS}/${id}/subscriptions`),
      call(`${CUSTOMERS}/${id}/offers`),
    ])
    if (sub.status === 404 || off.status === 404) return null
    if (!s.ok || !sub.ok || !off.ok) throw new Error('the server did not respond')
    return { shops: s.data, subs: sub.data, offers: off.data }
  }

  function apply(loaded) {
    setShops(loaded.shops)
    setFollowed(new Set(loaded.subs.map((x) => x.shopId)))
    setOffers(loaded.offers)
  }

  async function start() {
    setLoading(true)
    setError('')
    try {
      let id = readStoredId()
      if (!id) {
        id = await createCustomer()
        storeId(id)
      }
      let loaded = await loadFor(id)
      if (loaded === null) {
        // stored id is stale (database was reset): start over with a new customer
        clearStoredId()
        id = await createCustomer()
        storeId(id)
        loaded = await loadFor(id)
      }
      setCustomerId(id)
      apply(loaded)
    } catch (e) {
      setError('Could not load shops: ' + e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (started.current) return // React runs effects twice in development
    started.current = true
    start()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function refresh() {
    const loaded = await loadFor(customerId)
    if (loaded) apply(loaded)
    else start()
  }

  async function toggleFollow(shopId) {
    setError('')
    const following = followed.has(shopId)
    const r = await call(`${CUSTOMERS}/${customerId}/subscriptions/${shopId}`, {
      method: following ? 'DELETE' : 'PUT',
    })
    if (!r.ok && r.status !== 204) {
      setError('Could not update. Please try again.')
      return
    }
    await refresh()
  }

  function askLocation() {
    if (locationAsked.current) return
    locationAsked.current = true
    if (!navigator.geolocation) return
    navigator.geolocation.getCurrentPosition(
      (p) => setLocation({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => {}, // denied or timed out: show shops without distances
      { timeout: 5000 },
    )
  }

  function openTab(key) {
    setTab(key)
    if (key === 'nearby') askLocation()
  }

  const followedShops = shops.filter((s) => followed.has(s.id))
  const withDistance = shops
    .map((s) => ({
      ...s,
      km: location && s.latitude != null ? distanceKm(location, { lat: s.latitude, lng: s.longitude }) : null,
    }))
    .sort((a, b) => (a.km ?? Infinity) - (b.km ?? Infinity))

  return (
    <div className="cust">
      <header className="cust-top">
        <div className="cust-brand">
          <span className="cust-mark">BB</span>
          <span>Bachat Basket</span>
        </div>
        <span className="cust-sub">Offers near you</span>
      </header>

      <main className="cust-body">
        {error && <div className="cust-alert">{error}</div>}
        {loading && <div className="cust-muted">Loading shops…</div>}

        {!loading && tab === 'home' && (
          <>
            {followedShops.length === 0 && (
              <Empty
                title="Follow a shop to see its offers"
                action="Find shops nearby"
                onAction={() => openTab('nearby')}
              />
            )}
            {followedShops.map((shop) => {
              const list = offers[shop.id] || []
              const link = directionsUrl(shop)
              return (
                <section className="cust-card" key={shop.id}>
                  <div className="cust-row">
                    <div>
                      <h3>{shop.name}</h3>
                      {shop.closingTime && <div className="cust-muted">offer ends {hhmm(shop.closingTime)}</div>}
                    </div>
                    {link && (
                      <a className="cust-link" href={link} target="_blank" rel="noreferrer">Directions</a>
                    )}
                  </div>
                  {list.length === 0 && <div className="cust-muted cust-gap">No offers right now.</div>}
                  {list.map((l) => (
                    <div className="cust-offer" key={l.id}>
                      <div>
                        <div className="cust-item">{l.itemName}</div>
                        <div className="cust-muted">{l.quantity} {unitLabel(l.quantity, l.unit)}</div>
                        <div className="cust-muted">ends {endsLabel(l.endsAt)}</div>
                      </div>
                      <div className="cust-price-box">
                        <span className="cust-price">₹{l.offerPrice}</span>
                        {l.originalPrice != null && <span className="cust-old">₹{l.originalPrice}</span>}
                        {l.discountPercent != null && <span className="cust-badge">{l.discountPercent}% off</span>}
                      </div>
                    </div>
                  ))}
                </section>
              )
            })}
          </>
        )}

        {!loading && tab === 'nearby' && (
          <>
            <div className="cust-muted cust-gap">
              {location
                ? 'Shops sorted by distance from you.'
                : 'Allow location to see how far each shop is. Shops are listed without distances until then.'}
            </div>
            {withDistance.map((shop) => (
              <ShopRow
                key={shop.id}
                shop={shop}
                distance={shop.km == null ? null : formatKm(shop.km)}
                following={followed.has(shop.id)}
                onToggle={() => toggleFollow(shop.id)}
              />
            ))}
          </>
        )}

        {!loading && tab === 'follow' && (
          <>
            {followedShops.length === 0 && (
              <Empty
                title="You are not following any shop yet"
                action="Find shops nearby"
                onAction={() => openTab('nearby')}
              />
            )}
            {followedShops.length > 0 && (
              <div className="cust-muted cust-gap">Shops you follow. Their offers show on Home.</div>
            )}
            {followedShops.map((shop) => (
              <ShopRow
                key={shop.id}
                shop={shop}
                following
                onToggle={() => toggleFollow(shop.id)}
              />
            ))}
          </>
        )}

        {!loading && tab === 'deal' && (
          <section className="cust-card">
            <h3>Find a deal</h3>
            <p className="cust-muted">
              Ask about today's offers from the shops you follow, by voice or by typing.
              This is the next step to be built.
            </p>
            <input className="cust-input" placeholder="e.g. where is the lowest tomato price?" disabled />
            <button className="cust-btn" disabled>Ask</button>
          </section>
        )}

        <p className="cust-note">Prices are set by each shop. Original price is the price before the discount.</p>
      </main>

      <nav className="cust-tabs" aria-label="Customer sections">
        {TABS.map((t) => (
          <button
            key={t.key}
            className={tab === t.key ? 'on' : ''}
            onClick={() => openTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </nav>
    </div>
  )
}

function ShopRow({ shop, distance, following, onToggle }) {
  return (
    <section className="cust-card cust-row">
      <div>
        <h3>{shop.name}</h3>
        <div className="cust-muted">
          {distance ? `${distance} · ` : ''}
          {shop.closingTime ? `closes ${hhmm(shop.closingTime)}` : ''}
        </div>
      </div>
      <button className={'cust-btn small' + (following ? ' alt' : '')} onClick={onToggle}>
        {following ? 'Following' : 'Follow'}
      </button>
    </section>
  )
}

function Empty({ title, action, onAction }) {
  return (
    <section className="cust-card cust-empty">
      <h3>{title}</h3>
      <button className="cust-btn small" onClick={onAction}>{action}</button>
    </section>
  )
}
