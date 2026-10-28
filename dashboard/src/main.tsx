import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import './styles.css'
import './admin.css'
import './pages.css'

document.documentElement.dataset.theme = localStorage.getItem('riceguard.theme') === 'dark' ? 'dark' : 'light'
const queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: 30000, retry: 1, refetchOnWindowFocus: false } } })
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={queryClient}><App /></QueryClientProvider></React.StrictMode>)
