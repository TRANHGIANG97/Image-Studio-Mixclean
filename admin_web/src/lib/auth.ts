import { createServerSideSupabase } from '@/lib/supabase/server';
import { redirect } from 'next/navigation';

/**
 * Server-side helper: ensures the user is authenticated.
 * Redirects to /login if no valid session is found.
 * Use this in Server Components or Route Handlers that require auth.
 */
export async function requireAuth() {
  const supabase = await createServerSideSupabase();
  const {
    data: { session },
  } = await supabase.auth.getSession();

  if (!session) {
    redirect('/login');
  }

  return session;
}

/**
 * Returns the current session or null without redirecting.
 * Useful for optional auth checks.
 */
export async function getSession() {
  const supabase = await createServerSideSupabase();
  const {
    data: { session },
  } = await supabase.auth.getSession();
  return session;
}
