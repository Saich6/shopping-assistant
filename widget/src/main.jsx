import { createRoot } from 'react-dom/client';
import { Widget } from './Widget.jsx';
import { readConfig } from './config.js';

// Entry point of the single injectable script (see vite.config.js).
if (!window.__shoppingAssistantLoaded) { // never inject twice
  window.__shoppingAssistantLoaded = true;
  const config = readConfig(document.currentScript); // must be read now: it is null once the script has finished

  const start = () => {
    // A custom tag, not a <div>: generic page rules such as "div{display:none}" do not match it.
    const host = document.createElement('shopping-assistant-root');
    host.id = 'shopping-assistant-root';
    // Shadow DOM: the page's CSS cannot break the widget, and the widget's CSS cannot leak into the page.
    const shadow = host.attachShadow({ mode: 'open' });
    const mountPoint = document.createElement('div');
    shadow.appendChild(mountPoint);
    document.body.appendChild(host);
    createRoot(mountPoint).render(<Widget config={config} />);
  };

  if (document.body) start();
  else document.addEventListener('DOMContentLoaded', start);
}
