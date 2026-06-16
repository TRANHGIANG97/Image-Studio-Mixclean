export interface TypographyPreset {
  id: string;
  name: string;
  type: 'basic' | 'artistic' | 'sale';
  preview: string;
  config: {
    text: string;
    fontFamily?: string;
    fontSize?: number;
    fill?: string | any;
    fontWeight?: string | number;
    fontStyle?: string;
    textAlign?: string;
    stroke?: string;
    strokeWidth?: number;
    shadow?: {
      color: string;
      blur: number;
      offsetX: number;
      offsetY: number;
    };
    textTransform?: 'uppercase' | 'lowercase' | 'none';
    textBackgroundColor?: string;
    charSpacing?: number;
    lineHeight?: number;
  };
}

export const TYPOGRAPHY_PRESETS: TypographyPreset[] = [
  // --- BASIC TEXT ---
  {
    id: 'basic-title',
    name: 'Thêm Tiêu đề',
    type: 'basic',
    preview: 'Thêm Tiêu đề',
    config: {
      text: 'Tiêu đề chính',
      fontSize: 120,
      fontWeight: 'bold',
      fill: '#1e293b', // slate-800
      textAlign: 'center',
    }
  },
  {
    id: 'basic-subtitle',
    name: 'Thêm Tiêu đề phụ',
    type: 'basic',
    preview: 'Thêm Tiêu đề phụ',
    config: {
      text: 'Tiêu đề phụ',
      fontSize: 80,
      fontWeight: 'bold',
      fill: '#475569', // slate-600
      textAlign: 'center',
    }
  },
  {
    id: 'basic-body',
    name: 'Thêm Đoạn văn bản',
    type: 'basic',
    preview: 'Thêm Đoạn văn bản nhỏ',
    config: {
      text: 'Đoạn văn bản mẫu. Nhấp đúp để chỉnh sửa.',
      fontSize: 40,
      fontWeight: 'normal',
      fill: '#64748b', // slate-500
      textAlign: 'left',
      lineHeight: 1.5,
    }
  },

  // --- SALE / E-COMMERCE ---
  {
    id: 'sale-flash',
    name: 'Flash Sale Đậm',
    type: 'sale',
    preview: 'FLASH SALE',
    config: {
      text: 'FLASH SALE',
      fontSize: 140,
      fontWeight: 900,
      fontStyle: 'italic',
      fill: '#ffffff',
      stroke: '#ef4444', // red-500
      strokeWidth: 8,
      textTransform: 'uppercase',
      shadow: {
        color: 'rgba(0,0,0,0.4)',
        blur: 0,
        offsetX: 10,
        offsetY: 10
      }
    }
  },
  {
    id: 'sale-discount',
    name: 'Giảm Giá Tròn',
    type: 'sale',
    preview: 'GIẢM 50%',
    config: {
      text: 'GIẢM 50%',
      fontSize: 150,
      fontWeight: 900,
      fill: '#facc15', // yellow-400
      stroke: '#000000',
      strokeWidth: 6,
      textTransform: 'uppercase',
      shadow: {
        color: 'rgba(0,0,0,0.8)',
        blur: 5,
        offsetX: 6,
        offsetY: 6
      }
    }
  },
  {
    id: 'sale-freeship',
    name: 'Freeship Xtra',
    type: 'sale',
    preview: 'FREESHIP XTRA',
    config: {
      text: 'FREESHIP XTRA',
      fontSize: 110,
      fontWeight: 800,
      fontStyle: 'italic',
      fill: '#22c55e', // green-500
      stroke: '#ffffff',
      strokeWidth: 4,
      textTransform: 'uppercase',
      shadow: {
        color: '#166534', // green-800
        blur: 0,
        offsetX: 4,
        offsetY: 4
      }
    }
  },

  // --- ARTISTIC / NEON / GLOW ---
  {
    id: 'art-neon-pink',
    name: 'Neon Hồng',
    type: 'artistic',
    preview: 'NEON GLOW',
    config: {
      text: 'Neon Vibe',
      fontSize: 160,
      fontWeight: 800,
      fill: '#ffffff',
      shadow: {
        color: '#ec4899', // pink-500
        blur: 25,
        offsetX: 0,
        offsetY: 0
      }
    }
  },
  {
    id: 'art-outline-bold',
    name: 'Chữ rỗng viền đậm',
    type: 'artistic',
    preview: 'OUTLINE',
    config: {
      text: 'OUTLINE',
      fontSize: 180,
      fontWeight: 900,
      fill: 'transparent',
      stroke: '#0ea5e9', // sky-500
      strokeWidth: 6,
      textTransform: 'uppercase',
    }
  },
  {
    id: 'art-retro-3d',
    name: 'Retro 3D Offset',
    type: 'artistic',
    preview: 'RETRO 3D',
    config: {
      text: 'RETRO POP',
      fontSize: 150,
      fontWeight: 900,
      fill: '#f43f5e', // rose-500
      textTransform: 'uppercase',
      shadow: {
        color: '#0ea5e9', // sky-500
        blur: 0,
        offsetX: 8,
        offsetY: 8
      }
    }
  },
  {
    id: 'art-elegant-gold',
    name: 'Vàng Đồng Sang Trọng',
    type: 'artistic',
    preview: 'Luxury',
    config: {
      text: 'Premium Quality',
      fontSize: 100,
      fontWeight: 600,
      fill: '#eab308', // will be replaced with gradient in the component logic if we want, or just gold color
      fontFamily: 'serif',
      shadow: {
        color: 'rgba(0,0,0,0.3)',
        blur: 10,
        offsetX: 2,
        offsetY: 4
      }
    }
  }
];
