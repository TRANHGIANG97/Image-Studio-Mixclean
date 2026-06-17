'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { 
  LayoutDashboard, 
  Layers, 
  FolderHeart, 
  Image as ImageIcon,
  Sparkles,
  ChevronRight,
  Menu,
  X,
  LogOut
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { createClientSideSupabase } from '@/lib/supabase/client';

const navItems = [
  { href: '/', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/templates', label: 'Templates', icon: Layers },
  { href: '/categories', label: 'Categories', icon: FolderHeart },
  { href: '/assets', label: 'Asset Library', icon: ImageIcon },
];

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const [mobileMenuOpen, setMobileMenuOpen] = React.useState(false);

  const handleSignOut = async () => {
    const supabase = createClientSideSupabase();
    await supabase.auth.signOut();
    router.push('/login');
    router.refresh();
  };

  return (
    <div className="flex h-screen bg-white text-slate-800 font-sans overflow-hidden">
      {/* Desktop Sidebar */}
      <aside className="hidden md:flex md:w-64 md:flex-col fixed md:inset-y-0 z-20 bg-white/85 backdrop-blur-xl border-r border-slate-200/80 shadow-lg">
        <div className="flex items-center gap-2 px-6 h-16 border-b border-slate-200/80">
          <div className="p-1.5 rounded-lg bg-indigo-600 text-white">
            <Sparkles className="w-5 h-5" />
          </div>
          <span className="font-bold text-lg bg-gradient-to-r from-indigo-400 to-cyan-400 bg-clip-text text-transparent">
            Studio Admin
          </span>
        </div>
        
        <nav className="flex-1 px-4 py-6 space-y-1">
          {navItems.map((item) => {
            const isActive = pathname === item.href || (item.href !== '/' && pathname.startsWith(item.href));
            const Icon = item.icon;
            return (
              <Link key={item.href} href={item.href}>
                <span className={`flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 group ${
                  isActive 
                    ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/20' 
                    : 'text-slate-500 hover:bg-slate-100 hover:text-slate-800'
                }`}>
                  <Icon className={`w-5 h-5 transition-transform duration-200 group-hover:scale-110 ${
                    isActive ? 'text-white' : 'text-slate-500 group-hover:text-slate-800'
                  }`} />
                  {item.label}
                  {isActive && <ChevronRight className="w-4 h-4 ml-auto" />}
                </span>
              </Link>
            );
          })}
        </nav>

        <div className="p-4 border-t border-slate-200 space-y-2">
          <Button
            variant="ghost"
            size="sm"
            className="w-full justify-start gap-3 px-4 py-2.5 rounded-xl text-sm font-medium text-slate-500 hover:bg-rose-50 hover:text-rose-600 transition-all"
            onClick={handleSignOut}
          >
            <LogOut className="w-5 h-5" />
            Đăng xuất
          </Button>
          <p className="text-xs text-center text-slate-400 font-medium">Solo Dev Admin v1.0.0</p>
        </div>
      </aside>

      {/* Mobile Sidebar & Header */}
      <div className="flex flex-col flex-1 md:pl-64 h-full overflow-hidden">
        {/* Mobile Header */}
        <header className="flex items-center justify-between h-16 px-4 border-b border-slate-200 bg-white/85 backdrop-blur-md sticky top-0 z-10 md:hidden">
          <div className="flex items-center gap-2">
            <div className="p-1 rounded-md bg-indigo-600 text-white">
              <Sparkles className="w-4 h-4" />
            </div>
            <span className="font-bold text-sm bg-gradient-to-r from-indigo-400 to-cyan-400 bg-clip-text text-transparent">
              Studio Admin
            </span>
          </div>
          <Button 
            variant="ghost" 
            size="icon" 
            className="text-slate-500 hover:text-slate-800"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          >
            {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
          </Button>
        </header>

        {/* Mobile Navigation Drawer */}
        {mobileMenuOpen && (
          <div className="fixed inset-0 z-30 md:hidden bg-white/90 backdrop-blur-sm">
            <div className="fixed inset-y-0 left-0 w-64 bg-white border-r border-slate-200 flex flex-col p-4">
              <div className="flex items-center justify-between h-12 border-b border-slate-200 mb-6">
                <span className="font-bold text-md text-indigo-600">Navigation</span>
                <Button 
                  variant="ghost" 
                  size="icon" 
                  className="text-slate-500"
                  onClick={() => setMobileMenuOpen(false)}
                >
                  <X className="w-5 h-5" />
                </Button>
              </div>
              <nav className="flex-1 space-y-2">
                {navItems.map((item) => {
                  const isActive = pathname === item.href || (item.href !== '/' && pathname.startsWith(item.href));
                  const Icon = item.icon;
                  return (
                    <Link key={item.href} href={item.href} onClick={() => setMobileMenuOpen(false)}>
                      <span className={`flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-all ${
                        isActive 
                          ? 'bg-indigo-600 text-white' 
                          : 'text-slate-500 hover:bg-slate-100'
                      }`}>
                        <Icon className="w-5 h-5" />
                        {item.label}
                      </span>
                    </Link>
                  );
                })}
              </nav>
              <div className="pt-4 border-t border-slate-200 mt-auto">
                <Button
                  variant="ghost"
                  size="sm"
                  className="w-full justify-start gap-3 px-4 py-2.5 rounded-lg text-sm font-medium text-slate-500 hover:bg-rose-50 hover:text-rose-600 transition-all"
                  onClick={() => {
                    setMobileMenuOpen(false);
                    handleSignOut();
                  }}
                >
                  <LogOut className="w-5 h-5" />
                  Đăng xuất
                </Button>
              </div>
            </div>
          </div>
        )}

        {/* Main Workspace Area */}
        {pathname.endsWith('/edit') ? (
          <main className="flex-1 min-h-0 overflow-hidden">
            {children}
          </main>
        ) : (
          <main className="flex-1 p-6 md:p-8 max-w-7xl mx-auto w-full overflow-y-auto">
            {children}
          </main>
        )}
      </div>
    </div>
  );
}
