import { fetchFontsManifest, injectFontFace } from '@/lib/fonts-manifest';
import { resolveFontEntry } from '@/domains/fonts/font.manifest-utils';

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
 * Loads a font family using the shared /api/v1/fonts manifest.
 * Falls back to Google Fonts CSS when not in manifest.
 */
export const ensureFontLoaded = (fontFamily: string): Promise<void> => {
  if (!fontFamily) return Promise.resolve();

  const cleanFamily = fontFamily.split(',')[0]?.trim() ?? fontFamily.trim();
  const lowerFamily = cleanFamily.toLowerCase();

  if (loadedFonts.has(cleanFamily) || WEB_SAFE_FONTS.has(lowerFamily)) {
    return Promise.resolve();
  }

  if (typeof document !== 'undefined' && document.fonts.check(`12px "${cleanFamily}"`)) {
    loadedFonts.add(cleanFamily);
    return Promise.resolve();
  }

  return fetchFontsManifest()
    .then((manifest) => {
      const entry = resolveFontEntry(manifest.fonts, cleanFamily);
      if (entry?.font_url) {
        injectFontFace(entry);
        loadedFonts.add(cleanFamily);
        return document.fonts.load(`12px "${cleanFamily}"`).then(() => undefined).catch(() => undefined);
      }
      return loadGoogleFont(cleanFamily, lowerFamily);
    })
    .catch(() => loadGoogleFont(cleanFamily, lowerFamily));
};

function loadGoogleFont(cleanFamily: string, lowerFamily: string): Promise<void> {
  return new Promise<void>((resolve) => {
    if (typeof document === 'undefined') {
      resolve();
      return;
    }

    const linkId = `google-font-${lowerFamily.replace(/\s+/g, '-')}`;
    if (document.getElementById(linkId)) {
      document.fonts
        .load(`12px "${cleanFamily}"`)
        .then(() => resolve())
        .catch(() => resolve());
      return;
    }

    const link = document.createElement('link');
    link.id = linkId;
    link.rel = 'stylesheet';

    const formattedFamily = cleanFamily.replace(/\s+/g, '+');
    link.href = `https://fonts.googleapis.com/css2?family=${formattedFamily}:ital,wght@0,300;0,400;0,500;0,600;0,700;0,800;0,900;1,300;1,400;1,500;1,600;1,700;1,800;1,900&display=swap`;

    link.onload = () => {
      loadedFonts.add(cleanFamily);
      document.fonts
        .load(`12px "${cleanFamily}"`)
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
        document.fonts
          .load(`12px "${cleanFamily}"`)
          .then(() => resolve())
          .catch(() => resolve());
      };

      fallbackLink.onerror = () => resolve();
      link.remove();
      document.head.appendChild(fallbackLink);
    };

    document.head.appendChild(link);
  });
}
