import { NextResponse, type NextRequest } from 'next/server';
import { createServerClient } from '@supabase/ssr';

/**
 * Middleware: Protect all dashboard routes.
 * Redirects unauthenticated users to /login.
 * Refreshes the session cookie on every request to prevent expiry.
 */
export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Bypass middleware for all API routes to prevent body stream corruption
  if (pathname.startsWith('/api')) {
    return NextResponse.next();
  }

  let response = request.method === 'GET' 
    ? NextResponse.next({ request })
    : NextResponse.next();

  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return request.cookies.getAll();
        },
        setAll(cookiesToSet) {
          cookiesToSet.forEach(({ name, value }) => request.cookies.set(name, value));
          response = request.method === 'GET'
            ? NextResponse.next({ request })
            : NextResponse.next();
          cookiesToSet.forEach(({ name, value, options }) =>
            response.cookies.set(name, value, options)
          );
        },
      },
    }
  );

  // Refresh session — important to keep user logged in
  const {
    data: { session },
  } = await supabase.auth.getSession();

  // Public paths that don't require auth
  const isPublicPath = pathname.startsWith('/login') || pathname.startsWith('/api/v1');

  if (!session && !isPublicPath) {
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('redirectTo', pathname);
    return NextResponse.redirect(loginUrl);
  }

  // If already logged in and visiting /login, redirect to dashboard
  if (session && pathname === '/login') {
    return NextResponse.redirect(new URL('/', request.url));
  }

  return response;
}

export const config = {
  matcher: [
    /*
     * Match all routes EXCEPT:
     * - _next/static (static files)
     * - _next/image  (image optimization)
     * - favicon.ico
     */
    '/((?!_next/static|_next/image|favicon.ico).*)',
  ],
};
