const loadedFonts = new Set<string>();

const WEB_SAFE_FONTS = new Set([
  'sans-serif',
  'serif',
  'monospace',
  'cursive',
  'fantasy',
  'arial',
  'helvetica',
  'times new roman',
  'times',
  'courier new',
  'courier',
  'georgia',
  'verdana',
  'trebuchet ms',
  'impact',
  'comic sans ms',
  'tahoma',
]);

/**
 * Dynamically injects and loads a font family from Google Fonts if it's not a web-safe font.
 * Reresolves immediately if the font is already loaded or is web-safe.
 */
let cachedCustomFontsPromise: Promise<any[]> | null = null;
function getCustomFonts() {
  if (typeof window === 'undefined') return Promise.resolve([]);
  if (!cachedCustomFontsPromise) {
    cachedCustomFontsPromise = fetch('/api/v1/fonts')
      .then(res => res.ok ? res.json() : { success: false, fonts: [] })
      .then(data => data.success ? data.fonts : [])
      .catch(() => []);
  }
  return cachedCustomFontsPromise;
}

export const ensureFontLoaded = (fontFamily: string): Promise<void> => {
  if (!fontFamily) return Promise.resolve();

  const cleanFamily = fontFamily.trim();
  const lowerFamily = cleanFamily.toLowerCase();

  // If already loaded or web-safe, skip
  if (loadedFonts.has(cleanFamily) || WEB_SAFE_FONTS.has(lowerFamily)) {
    return Promise.resolve();
  }

  // Check if browser already has this font loaded
  if (typeof document !== 'undefined' && document.fonts.check(`12px "${cleanFamily}"`)) {
    loadedFonts.add(cleanFamily);
    return Promise.resolve();
  }

  return new Promise<void>((resolve) => {
    if (typeof document === 'undefined') {
      resolve();
      return;
    }

    // Try custom database fonts first
    getCustomFonts().then((customFonts) => {
      const matchedFont = customFonts.find(f => 
        f.family_slug.toLowerCase() === lowerFamily ||
        f.name.toLowerCase().replace(/\s+/g, '') === lowerFamily.replace(/\s+/g, '')
      );

      if (matchedFont) {
        const fontId = `dynamic-font-${matchedFont.family_slug}`;
        if (!document.getElementById(fontId)) {
          const style = document.createElement('style');
          style.id = fontId;
          const cleanName = matchedFont.name.replace(/\s+/g, '');
          style.appendChild(document.createTextNode(`
            @font-face {
              font-family: '${matchedFont.family_slug}';
              src: url('${matchedFont.font_url}') format('truetype');
              font-display: swap;
            }
            @font-face {
              font-family: '${matchedFont.name}';
              src: url('${matchedFont.font_url}') format('truetype');
              font-display: swap;
            }
            @font-face {
              font-family: '${cleanName}';
              src: url('${matchedFont.font_url}') format('truetype');
              font-display: swap;
            }
          `));
          document.head.appendChild(style);
        }

        loadedFonts.add(cleanFamily);
        document.fonts.load(`12px "${cleanFamily}"`)
          .then(() => resolve())
          .catch(() => resolve());
        return;
      }

      // Fallback to Google Fonts load logic
      const linkId = `google-font-${lowerFamily.replace(/\s+/g, '-')}`;
      if (document.getElementById(linkId)) {
        document.fonts.load(`12px "${cleanFamily}"`)
          .then(() => resolve())
          .catch(() => resolve());
        return;
      }

      const link = document.createElement('link');
      link.id = linkId;
      link.rel = 'stylesheet';

      const formattedFamily = cleanFamily.replace(/\s+/g, '+');
      link.href = `https://fonts.googleapis.com/css2?family=${formattedFamily}:ital,wght@0,300;0,400;0,500;0,600;0,700;0,800;0,900;1,300;1,400;1,500;1,600;1,700;1,800;1,900&family=${formattedFamily}&display=swap`;

      link.onload = () => {
        loadedFonts.add(cleanFamily);
        document.fonts.load(`12px "${cleanFamily}"`)
          .then(() => resolve())
          .catch(() => resolve());
      };

      link.onerror = () => {
        const fallbackLink = document.createElement('link');
        fallbackLink.id = linkId;
        fallbackLink.rel = 'stylesheet';
        fallbackLink.href = `https://fonts.googleapis.com/css2?family=${formattedFamily}&display=swap`;
        
        fallbackLink.onload = () => {
          loadedFonts.add(cleanFamily);
          document.fonts.load(`12px "${cleanFamily}"`)
            .then(() => resolve())
            .catch(() => resolve());
        };
        
        fallbackLink.onerror = () => {
          resolve();
        };

        link.remove();
        document.head.appendChild(fallbackLink);
      };

      document.head.appendChild(link);
    });
  });
};
