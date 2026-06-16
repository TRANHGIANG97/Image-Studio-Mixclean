import { createServerClient } from '@supabase/ssr';

/**
 * Server-side Supabase client (used in Server Components, Route Handlers, and Server Actions).
 * Handles cookie-based session management for authenticated requests.
 */
export const createServerSideSupabase = async () => {
  // We dynamically import cookies to avoid bundling issues on the client side
  const { cookies } = await import('next/headers');
  const cookieStore = await cookies();

  return createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return cookieStore.getAll();
        },
        setAll(cookiesToSet) {
          try {
            cookiesToSet.forEach(({ name, value, options }) =>
              cookieStore.set(name, value, options)
            );
          } catch {
            // The `setAll` method was called from a Server Component.
            // This can be ignored if you have middleware refreshing
            // user sessions.
          }
        },
      },
    }
  );
};
