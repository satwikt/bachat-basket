import { Link } from 'react-router-dom'

export default function Home() {
  return (
    <div className="app home">
      <h1>Bachat Basket</h1>
      <p className="tag">
        Neighbourhood shops list near-expiry food at a price they choose. Nearby customers
        walk in and buy it. The food gets used, not binned.
      </p>
      <Link className="btn" to="/merchant">I am a shopkeeper</Link>
      <Link className="btn alt" to="/customer">I am a customer</Link>
      <p className="note">Prototype screens.</p>
    </div>
  )
}
