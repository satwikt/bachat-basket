import { Routes, Route, Navigate } from 'react-router-dom'
import Home from './pages/Home.jsx'
import Merchant from './pages/Merchant.jsx'
import Customer from './pages/Customer.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/merchant" element={<Merchant />} />
      <Route path="/customer" element={<Customer />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
