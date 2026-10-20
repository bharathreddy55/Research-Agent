export const API_ORIGIN = (import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:8085').replace(/\/$/, '');
type Csrf = { headerName: string; token: string };
let csrf: Csrf | null = null;

export async function refreshCsrf(): Promise<Csrf> {
  const response = await fetch(`${API_ORIGIN}/api/auth/csrf`, { credentials: 'include', cache: 'no-store' });
  if (!response.ok) throw new Error('Cannot connect to the sign-in service');
  csrf = await response.json() as Csrf;
  return csrf;
}

export async function sessionFetch(url: string, options: RequestInit = {}): Promise<Response> {
  const headers = new Headers(options.headers);
  if (!['GET', 'HEAD', 'OPTIONS'].includes((options.method ?? 'GET').toUpperCase())) {
    const token = csrf ?? await refreshCsrf();
    headers.set(token.headerName, token.token);
  }
  const response = await fetch(url, { ...options, headers, credentials: 'include', cache: 'no-store' });
  if (response.status === 401 && !url.endsWith('/login')) {
    csrf = null;
    window.dispatchEvent(new Event('session-expired'));
  }
  return response;
}

export async function login(username: string, password: string): Promise<string> {
  await refreshCsrf();
  const response = await sessionFetch(`${API_ORIGIN}/api/auth/login`, {
    method: 'POST',
    body: new URLSearchParams({ username, password }),
  });
  if (!response.ok) throw new Error(response.status === 401
    ? 'Invalid username or password' : 'Sign-in failed. Please try again.');
  const user = await response.json() as { username: string };
  // Authentication rotates the session and clears the previous CSRF token.
  csrf = null;
  await refreshCsrf();
  return user.username;
}

export async function logout(): Promise<void> {
  // Refresh for sessions also used in other browser tabs.
  await refreshCsrf();
  const response = await sessionFetch(`${API_ORIGIN}/api/auth/logout`, { method: 'POST' });
  if (!response.ok && response.status !== 401) throw new Error('Sign-out failed. Please try again.');
  csrf = null;
}
