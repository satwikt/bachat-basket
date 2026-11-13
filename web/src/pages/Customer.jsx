import { useState } from 'react'
import { Link } from 'react-router-dom'

const SHOPS = [
  {
    id: 1, name: 'Sharma General Store', distance: '0.4 km', until: '9:00 pm',
    items: [
      { name: 'Milk 500 ml', price: 20, usual: 27 },
      { name: 'Bread', price: 15, usual: 30 },
    ],
  },
  {
    id: 2, name: 'Patil Dairy and Provisions', distance: '0.9 km', until: '8:30 pm',
    items: [
      { name: 'Eggs (dozen)', price: 50, usual: 84 },
      { name: 'Curd cup', price: 12, usual: 20 },
    ],
  },
  {
    id: 3, name: 'Fresh Mart', distance: '1.6 km', until: '10:00 pm',
    items: [{ name: 'Tomatoes 1 kg', price: 18, usual: 40 }],
  },
]

export default function Customer() {
  const [subscribed, setSubscribed] = useState({ 1: true, 2: true })
  const toggle = (id) => setSubscribed({ ...subscribed, [id]: !subscribed[id] })

  return (
    <div className="app">
      <div className="topbar">
        <h1>Offer finder · near you</h1>
        <Link to="/">Home</Link>
      </div>
      <div className="pad">
        <div className="muted" style={{ marginBottom: 12 }}>
          Near-expiry deals from shops you follow. Walk in and pay with the usual Paytm QR.
        </div>
        {SHOPS.map((s) => (
          <div className="card" key={s.id}>
            <div className="row">
              <div>
                <h3>{s.name}</h3>
                <div className="muted">{s.distance} · offer ends {s.until}</div>
              </div>
              <button
                className={'btn small ' + (subscribed[s.id] ? 'alt' : '')}
                onClick={() => toggle(s.id)}
              >
                {subscribed[s.id] ? 'Following' : 'Follow'}
              </button>
            </div>
            {subscribed[s.id] ? (
              <div style={{ marginTop: 12 }}>
                {s.items.map((it) => (
                  <div className="row" key={it.name} style={{ padding: '6px 0' }}>
                    <span>{it.name}</span>
                    <span>
                      <span className="price">₹{it.price}</span>
                      <span className="old">₹{it.usual}</span>
                    </span>
                  </div>
                ))}
                <a className="btn amber" href="https://www.google.com/maps" target="_blank" rel="noreferrer">
                  Directions
                </a>
              </div>
            ) : (
              <div className="muted" style={{ marginTop: 10 }}>Follow this shop to see its offers.</div>
            )}
          </div>
        ))}
        <p className="note">Sample data for illustration. Prices are set by each shop.</p>
      </div>
    </div>
  )
}
