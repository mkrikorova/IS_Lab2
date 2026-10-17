import React from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import App from './App.jsx';
import './styles.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {/* HashRouter: адреса вида /spacemarine/app/#/chapters — Payara ничего не нужно настраивать */}
    <HashRouter>
      <App />
    </HashRouter>
  </React.StrictMode>
);
