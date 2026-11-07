// Accept only a destination within this application.
export function safeReturnPath(value, fallback = '/books') {
  if (typeof value !== 'string' || !value.startsWith('/')
      || value.startsWith('//')
      || Array.from(value).some(char => char === '\\' || char.charCodeAt(0) < 32)) {
    return fallback;
  }
  const url = new URL(value, window.location.origin);
  if (url.origin !== window.location.origin
      || ['/', '/login', '/register'].includes(url.pathname)) {
    return fallback;
  }
  return `${url.pathname}${url.search}${url.hash}`;
}
