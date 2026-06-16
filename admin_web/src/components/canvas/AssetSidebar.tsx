'use client';

import React, { useEffect, useState } from 'react';
import { FabricImage, Rect, Circle, Triangle, Path } from 'fabric';
import { 
  Image as ImageIcon, 
  Search, 
  Loader2, 
  Plus,
  FolderOpen,
  Shapes,
  Smile,
  ImagePlus,
  Type
} from 'lucide-react';
import { IText, Shadow } from 'fabric';
import { TYPOGRAPHY_PRESETS, TypographyPreset } from '@/lib/typography-presets';
import { Input } from '@/components/ui/input';
import { useEditorStore } from '@/store/editor.store';
import { useLayersStore } from '@/store/layers.store';
import { toast } from 'sonner';

interface Asset {
  id: string;
  name: string;
  folder: string;
  file_url: string;
}

const createLayerId = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `layer_${crypto.randomUUID()}`;
  }
  return `layer_${Math.random().toString(36).substring(2, 15)}`;
};

const folders = [
  { id: 'all', label: 'Tất cả' },
  { id: 'backgrounds', label: 'Hình nền' },
  { id: 'objects', label: 'Vật phẩm' },
  { id: 'decorations', label: 'Trang trí' },
  { id: 'stickers', label: 'Stickers' },
  { id: 'borders', label: 'Khung viền' },
  { id: 'anh_chuyen_nghiep', label: 'Chuyên nghiệp' },
  { id: 'anh_my_pham', label: 'Mỹ phẩm' },
  { id: 'selfie_dam_me_an_uong', label: 'Mê ăn uống' },
  { id: 'doi_song_so', label: 'Đời sống số' },
  { id: 'imported-psd', label: 'PSD Imports' },
  { id: 'uncategorized', label: 'Khác' }
];

// 1. Curated Emojis for OpenMoji Vector Stickers
const emojiCategories = [
  {
    name: 'Biểu cảm',
    emojis: ['😀', '😂', '😍', '😎', '🥳', '😜', '🤔', '😡', '😱', '😴', '🤡', '👻', '👽', '🤖', '👑', '💖', '👍', '👎', '👏', '🌟', '🔥', '💥', '✨', '💯']
  },
  {
    name: 'Động vật',
    emojis: ['🐱', '🐶', '🦊', '🦁', '🐯', '🐼', '🐨', '🐰', '🐻', '🐵', 'Frog', '🦉', '🦅', '🦆', '🦋', '🐝', '🌸', '🌹', '🌻', '🍀', '🍁', '🌲', '🌵', '🌴']
  },
  {
    name: 'Ăn uống',
    emojis: ['🍎', '🍌', '🍇', '🍓', '🍒', '🍉', '🍕', '🍔', '🍟', '🌭', '🌮', '🍿', 'Sushi', '🍩', '🍰', '🧁', '🍦', '☕', '🍹', '🍷', '🍺', '🥤', '🧉']
  },
  {
    name: 'Hoạt động',
    emojis: ['⚽', '🏀', '🏈', '🎮', '🎨', '🎸', '🎹', '🎬', '📷', '✈️', '🚀', '🚗', '🚲', '🎡', '🏖️', '🏔️', '🏕️', '🌈', '⚡', '⛄', '🎈', '🎉', '🎁', '🏆']
  },
  {
    name: 'Ký hiệu',
    emojis: ['💡', '🔑', '📚', '💻', '📱', '✉️', '💵', '📈', '📌', '⚙️', '🔒', '🔓', '🔑', '🔔', '🎯', '💎', '❤️', '❌', '✅', '⚠️', '🚫', '⭐', '💤', '📣']
  }
];

// 2. Curated Unsplash background IDs (Standard Web API source compatible)
const unsplashBackgrounds = [
  { id: 'photo-1557683316-973673baf926', name: 'Pastel Violet' },
  { id: 'photo-1579546929518-9e396f3cc809', name: 'Spectral Wave' },
  { id: 'photo-1533090161767-e6ffed986c88', name: 'Statuary Marble' },
  { id: 'photo-1507525428034-b723cf961d3e', name: 'Tropic Beach' },
  { id: 'photo-1579783900882-c0d3dad7b119', name: 'Watercolor Art' },
  { id: 'photo-1501534159995-5b8c9dd37537', name: 'Leaf Shadow' },
  { id: 'photo-1508739773434-c26b3d09e071', name: 'Distant Stars' },
  { id: 'photo-1499951360447-b19be8fe80f5', name: 'Classic Wood' },
  { id: 'photo-1618005182384-a83a8bd57fbe', name: 'Fluid Geometric' },
  { id: 'photo-1502224562085-639556652f33', name: 'Gold Splatters' },
  { id: 'photo-1531315630201-bb15abeb1653', name: 'Abstract Nebula' },
  { id: 'photo-1600585154340-be6161a56a0c', name: 'Organic Shadows' },
];

const shapesList = [
  { type: 'rect' as const, name: 'Hình vuông', icon: '■' },
  { type: 'circle' as const, name: 'Hình tròn', icon: '●' },
  { type: 'triangle' as const, name: 'Hình tam giác', icon: '▲' },
  { type: 'star' as const, name: 'Ngôi sao', icon: '★' },
];

interface AssetSidebarProps {
  categoryId?: string;
  onDirty?: () => void;
}

export default function AssetSidebar({ categoryId, onDirty }: AssetSidebarProps) {
  const { canvas, pushState } = useEditorStore();
  
  // Tabs config
  const [activeTab, setActiveTab] = useState<'local' | 'shapes' | 'stickers' | 'unsplash' | 'text'>('local');
  
  // Local assets state
  const [assets, setAssets] = useState<Asset[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeFolder, setActiveFolder] = useState('all');
  const [search, setSearch] = useState('');
  const [filterByCategory, setFilterByCategory] = useState(false);
  const [addingId, setAddingId] = useState<string | null>(null);
  const [hoveredAsset, setHoveredAsset] = useState<Asset | null>(null);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  
  // Stickers state
  const [activeStickerCategory, setActiveStickerCategory] = useState(emojiCategories[0].name);
  const [onlineStickers, setOnlineStickers] = useState<Asset[]>([]);
  const [loadingStickers, setLoadingStickers] = useState(false);
  const [stickerMode, setStickerMode] = useState<'online' | 'emoji'>('online');

  // Sync list of layers with Zustand store
  const syncStoreLayers = () => {
    if (!canvas) return;
    const objects = canvas.getObjects();
    const updatedLayers = objects
      .filter((obj: any) => obj._isBackground !== true)
      .map((obj: any) => ({
        id: obj.layerId || '',
        name: obj.layerName || 'Layer',
        type: obj.layerType || 'DECORATION',
        visible: obj.visible !== false,
        locked: obj.lockMovementX === true
      }));
    useLayersStore.getState().setLayers(updatedLayers);
  };

  // Fetch online stickers
  useEffect(() => {
    if (activeTab === 'stickers') {
      const fetchOnlineStickers = async () => {
        try {
          setLoadingStickers(true);
          const res = await fetch('/api/assets?folder=stickers&limit=150');
          const data = await res.json();
          if (res.ok && data.success) {
            setOnlineStickers(data.assets || []);
          }
        } catch (err) {
          console.error('Error loading online stickers:', err);
        } finally {
          setLoadingStickers(false);
        }
      };
      fetchOnlineStickers();
    }
  }, [activeTab]);

  // Add Online Sticker to Canvas
  const addStickerToCanvas = async (sticker: Asset) => {
    if (!canvas) return;
    setAddingId(sticker.id);

    try {
      const img = await FabricImage.fromURL(sticker.file_url, {
        crossOrigin: 'anonymous'
      });

      const baseWidth = canvas.width || 1080;
      const baseHeight = canvas.height || 1920;

      img.set({
        left: baseWidth / 2,
        top: baseHeight / 2,
        originX: 'center',
        originY: 'center',
      });

      (img as any).layerId = createLayerId();
      (img as any).layerType = 'IMAGE'; // Standard stickers are dynamic images
      (img as any).layerName = sticker.name;
      (img as any).src = sticker.file_url;
      (img as any).defaultImageUrl = sticker.file_url;

      img.scaleToWidth(baseWidth * 0.3);

      canvas.add(img);
      canvas.setActiveObject(img);
      canvas.renderAll();
      
      pushState();
      syncStoreLayers();
    } catch (err) {
      console.error('Failed to add sticker to canvas:', err);
      alert('Không thể chèn sticker vào canvas. Vui lòng thử lại.');
    } finally {
      setAddingId(null);
    }
  };

  // Add Text Preset to Canvas
  const addTextToCanvas = (preset: TypographyPreset) => {
    if (!canvas) return;
    const baseWidth = canvas.width || 1080;
    const baseHeight = canvas.height || 1920;

    const { shadow, text, ...restConfig } = preset.config;

    const textObj = new IText(text, {
      left: baseWidth / 2,
      top: baseHeight / 2,
      originX: 'center',
      originY: 'center',
      ...(restConfig as any),
      shadow: shadow ? new Shadow(shadow) : undefined,
    });

    (textObj as any).layerId = createLayerId();
    (textObj as any).layerType = 'TEXT';
    (textObj as any).layerName = preset.name;
    (textObj as any)._originalText = preset.config.text;

    canvas.add(textObj);
    canvas.setActiveObject(textObj);
    canvas.renderAll();
    pushState();
    syncStoreLayers();
    onDirty?.();
  };

  // 1. Local Database assets fetcher
  const fetchAssets = async (pageNum = 1, append = false) => {
    try {
      setLoading(pageNum === 1);
      const folderParam = activeFolder === 'all' ? '' : activeFolder;
      const categoryParam = (filterByCategory && categoryId) ? categoryId : '';
      const limitVal = 24;
      const res = await fetch(
        `/api/assets?folder=${folderParam}&search=${search}&categoryId=${categoryParam}&page=${pageNum}&limit=${limitVal}`
      );
      const data = await res.json();
      if (res.ok) {
        setAssets((prev) => (append ? [...prev, ...(data.assets || [])] : (data.assets || [])));
        setHasMore(data.hasMore || false);
      }
    } catch (err) {
      console.error('Error loading assets in sidebar:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'local') {
      setPage(1);
      fetchAssets(1, false);
    }
  }, [activeFolder, search, activeTab, filterByCategory, categoryId]);

  // Add Local Image to Canvas
  const addImageToCanvas = async (asset: Asset) => {
    if (!canvas) return;
    setAddingId(asset.id);

    try {
      const img = await FabricImage.fromURL(asset.file_url, {
        crossOrigin: 'anonymous'
      });

      const baseWidth = canvas.width || 1080;
      const baseHeight = canvas.height || 1920;

      // Nếu là thư mục backgrounds, đặt làm ảnh nền canvas thay vì layer thường
      if (asset.folder === 'backgrounds') {
        const scaleX = baseWidth / (img.width || baseWidth);
        const scaleY = baseHeight / (img.height || baseHeight);
        const bgScale = Math.max(scaleX, scaleY);

        img.set({
          originX: 'left',
          originY: 'top',
          left: 0,
          top: 0,
          scaleX: bgScale,
          scaleY: bgScale,
          selectable: false,
          evented: false,
          hasControls: false,
          hasBorders: false,
        });

        (img as any)._isBackground = true;
        (img as any).layerId = 'background_layer';
        (img as any).layerName = `BG: ${asset.name}`;
        (img as any).src = asset.file_url;
        (img as any).defaultImageUrl = asset.file_url;

        canvas.backgroundImage = img;
        canvas.renderAll();
        pushState();
        syncStoreLayers();
        onDirty?.();
        console.log(`[TPL_BG_DEBUG] asset background applied assetId=${asset.id} url=${asset.file_url} canvasBackgroundSrc=${(canvas.backgroundImage as any)?.src || 'null'}`);
        toast?.success('Đã cập nhật ảnh nền canvas!');
        setAddingId(null);
        return;
      }

      img.set({
        left: baseWidth / 2,
        top: baseHeight / 2,
        originX: 'center',
        originY: 'center',
      });

      (img as any).layerId = createLayerId();
      (img as any).layerType = 'IMAGE';
      (img as any).layerName = asset.name;
      (img as any).src = asset.file_url;
      (img as any).defaultImageUrl = asset.file_url;

      if (img.width && img.width > baseWidth * 0.5) {
        img.scaleToWidth(baseWidth * 0.5);
      }

      canvas.add(img);
      canvas.setActiveObject(img);
      canvas.renderAll();
      
      pushState();
      syncStoreLayers();
    } catch (err) {
      console.error('Failed to add image to canvas:', err);
      toast?.error('Không thể chèn ảnh vào canvas. Vui lòng thử lại.');
    } finally {
      setAddingId(null);
    }
  };

  const handleAssetDragStart = (asset: Asset) => (e: React.DragEvent<HTMLDivElement>) => {
    e.dataTransfer.effectAllowed = 'copy';
    e.dataTransfer.setData('application/json', JSON.stringify(asset));
    e.dataTransfer.setData('text/plain', asset.file_url);
  };

  // 2. Add Vector Shape to Canvas
  const addShapeToCanvas = (type: 'rect' | 'circle' | 'triangle' | 'star') => {
    if (!canvas) return;
    
    let shape: any;
    const baseWidth = canvas.width || 1080;
    const baseHeight = canvas.height || 1920;
    const commonProps = {
      left: baseWidth / 2,
      top: baseHeight / 2,
      originX: 'center' as const,
      originY: 'center' as const,
      fill: '#6366f1',
    };
    
    if (type === 'rect') {
      shape = new Rect({
        ...commonProps,
        width: 200,
        height: 200,
      });
    } else if (type === 'circle') {
      shape = new Circle({
        ...commonProps,
        radius: 100,
      });
    } else if (type === 'triangle') {
      shape = new Triangle({
        ...commonProps,
        width: 200,
        height: 200,
      });
    } else if (type === 'star') {
      const starPath = "M 100, 10 L 123, 67 L 186, 67 L 134, 104 L 154, 161 L 100, 125 L 46, 161 L 66, 104 L 14, 67 L 77, 67 Z";
      shape = new Path(starPath, {
        ...commonProps,
        scaleX: 1.5,
        scaleY: 1.5,
      });
    }
    
    if (shape) {
      shape.layerId = createLayerId();
      shape.layerType = 'DECORATION';
      shape.layerName = `${type.toUpperCase()} Shape`;
      
      canvas.add(shape);
      canvas.setActiveObject(shape);
      canvas.renderAll();
      pushState();
      syncStoreLayers();
    }
  };

  // Helper to map emoji characters to hex codes for OpenMoji
  const emojiToHex = (emoji: string) => {
    // Treat words like 'Frog' or 'Sushi' as direct hex assets mapping or simple fallbacks
    if (emoji === 'Frog') return '1F438';
    if (emoji === 'Sushi') return '1F363';
    
    const codePoints = Array.from(emoji).map(char => char.codePointAt(0)!.toString(16).toUpperCase());
    const cleanPoints = codePoints.filter(p => p !== 'FE0F');
    return cleanPoints.join('-');
  };

  // 3. Add CDN Emoji Sticker
  const addEmojiSticker = async (emoji: string) => {
    if (!canvas) return;
    setAddingId(emoji);
    
    try {
      const hex = emojiToHex(emoji);
      const url = `https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/svg/${hex}.svg`;
      
      const img = await FabricImage.fromURL(url, {
        crossOrigin: 'anonymous'
      });
      
      const baseWidth = canvas.width || 1080;
      const baseHeight = canvas.height || 1920;
      
      img.set({
        left: baseWidth / 2,
        top: baseHeight / 2,
        originX: 'center',
        originY: 'center',
      });
      
      (img as any).layerId = createLayerId();
      (img as any).layerType = 'IMAGE';
      (img as any).layerName = `Sticker ${emoji}`;
      (img as any).src = url;
      (img as any).defaultImageUrl = url;
      
      img.scaleToWidth(baseWidth * 0.35);
      
      canvas.add(img);
      canvas.setActiveObject(img);
      canvas.renderAll();
      pushState();
      syncStoreLayers();
    } catch (err) {
      console.error('Failed to load emoji sticker:', err);
      toast?.error('Không thể tải Sticker vector này. Vui lòng thử lại.');
    } finally {
      setAddingId(null);
    }
  };

  // 4. Add CDN Background Photo from Unsplash
  const addBackgroundPhoto = async (photoId: string, name: string) => {
    if (!canvas) return;
    setAddingId(photoId);
    
    try {
      const url = `https://images.unsplash.com/${photoId}?w=1200&auto=format&fit=crop&q=85`;
      
      const img = await FabricImage.fromURL(url, {
        crossOrigin: 'anonymous'
      });
      
      const baseWidth = canvas.width || 1080;
      const baseHeight = canvas.height || 1920;
      
      const scaleX = baseWidth / (img.width || baseWidth);
      const scaleY = baseHeight / (img.height || baseHeight);
      const bgScale = Math.max(scaleX, scaleY);

      img.set({
        originX: 'left',
        originY: 'top',
        left: 0,
        top: 0,
        scaleX: bgScale,
        scaleY: bgScale,
        selectable: false,
        evented: false,
        hasControls: false,
        hasBorders: false,
      });

      (img as any)._isBackground = true;
      (img as any).layerId = 'background_layer';
      (img as any).layerName = `Unsplash: ${name}`;
      (img as any).src = url;
      (img as any).defaultImageUrl = url;

      await canvas.setBackgroundImage(img, canvas.renderAll.bind(canvas));
      pushState();
      syncStoreLayers();
      onDirty?.();
      console.log(`[TPL_BG_DEBUG] unsplash background applied photoId=${photoId} url=${url} canvasBackgroundSrc=${(canvas.backgroundImage as any)?.src || 'null'}`);
      toast?.success('Đã cập nhật ảnh nền từ Unsplash!');
    } catch (err) {
      console.error('Failed to load background photo:', err);
      toast?.error('Không thể tải hình nền. Vui lòng thử lại.');
    } finally {
      setAddingId(null);
    }
  };

  return (
    <div className="flex flex-col h-full space-y-4">
      {/* Tab bar header */}
      <div className="grid grid-cols-5 gap-1 p-1 bg-slate-950 border border-slate-850 rounded-xl shrink-0">
        <button
          onClick={() => setActiveTab('local')}
          className={`flex flex-col items-center gap-1 py-1.5 rounded-lg text-[10px] font-bold transition-all ${
            activeTab === 'local' ? 'bg-indigo-600 text-white shadow' : 'text-slate-500 hover:text-slate-300'
          }`}
          title="Vật liệu của bạn"
        >
          <ImageIcon className="w-3.5 h-3.5" />
          <span>Vật liệu</span>
        </button>
        <button
          onClick={() => setActiveTab('shapes')}
          className={`flex flex-col items-center gap-1 py-1.5 rounded-lg text-[10px] font-bold transition-all ${
            activeTab === 'shapes' ? 'bg-indigo-600 text-white shadow' : 'text-slate-500 hover:text-slate-300'
          }`}
          title="Hình khối"
        >
          <Shapes className="w-3.5 h-3.5" />
          <span>Hình khối</span>
        </button>
        <button
          onClick={() => setActiveTab('stickers')}
          className={`flex flex-col items-center gap-1 py-1.5 rounded-lg text-[10px] font-bold transition-all ${
            activeTab === 'stickers' ? 'bg-indigo-600 text-white shadow' : 'text-slate-500 hover:text-slate-300'
          }`}
          title="Vectơ Emojis"
        >
          <Smile className="w-3.5 h-3.5" />
          <span>Stickers</span>
        </button>
        <button
          onClick={() => setActiveTab('unsplash')}
          className={`flex flex-col items-center gap-1 py-1.5 rounded-lg text-[10px] font-bold transition-all ${
            activeTab === 'unsplash' ? 'bg-indigo-600 text-white shadow' : 'text-slate-500 hover:text-slate-300'
          }`}
          title="Hình nền cao cấp"
        >
          <ImagePlus className="w-3.5 h-3.5" />
          <span>Hình nền</span>
        </button>
        <button
          onClick={() => setActiveTab('text')}
          className={`flex flex-col items-center gap-1 py-1.5 rounded-lg text-[10px] font-bold transition-all ${
            activeTab === 'text' ? 'bg-indigo-600 text-white shadow' : 'text-slate-500 hover:text-slate-300'
          }`}
          title="Văn bản"
        >
          <Type className="w-3.5 h-3.5" />
          <span>Văn bản</span>
        </button>
      </div>

      {/* Tab Content Area */}
      <div className="flex-1 flex flex-col min-h-0">
        
        {/* TAB 1: LOCAL ASSETS */}
        {activeTab === 'local' && (
          <div className="flex-1 flex flex-col space-y-3 min-h-0">
            {/* Search */}
            <div className="space-y-1.5 shrink-0">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-500" />
                <Input 
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Tìm vật liệu..."
                  className="pl-9 bg-slate-950 border-slate-800 text-xs text-slate-200 placeholder:text-slate-600 rounded-xl w-full focus-visible:ring-indigo-600"
                />
              </div>
              {categoryId && (
                <div className="flex items-center justify-between px-2.5 py-1 bg-slate-950/60 border border-slate-850/80 rounded-xl text-[9px] text-slate-400 select-none">
                  <span>Chỉ hiện tài nguyên cùng danh mục mẫu</span>
                  <input
                    type="checkbox"
                    checked={filterByCategory}
                    onChange={(e) => setFilterByCategory(e.target.checked)}
                    className="rounded text-indigo-650 focus:ring-indigo-600 bg-slate-900 border-slate-800 h-3 w-3 cursor-pointer"
                  />
                </div>
              )}
            </div>

            {/* Folder selector */}
            <div className="flex gap-1 overflow-x-auto py-1 no-scrollbar shrink-0 border-b border-slate-800/50 pb-2">
              {folders.map(f => (
                <button
                  key={f.id}
                  onClick={() => setActiveFolder(f.id)}
                  className={`px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider shrink-0 transition-all ${
                    activeFolder === f.id ? 'bg-indigo-600 text-white' : 'bg-slate-950 text-slate-500 hover:text-slate-300'
                  }`}
                >
                  {f.label}
                </button>
              ))}
            </div>

            {/* Grid */}
            <div className="flex-1 overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800">
              {hoveredAsset && (
                <div className="mb-3 rounded-2xl border border-slate-800 bg-slate-950 p-3 shadow-lg shadow-black/20">
                  <div className="flex items-center gap-3">
                    <div className="w-20 h-20 shrink-0 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-center overflow-hidden">
                      <img
                        src={hoveredAsset.file_url}
                        alt={hoveredAsset.name}
                        className="max-w-full max-h-full object-contain"
                        loading="lazy"
                      />
                    </div>
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-slate-100 truncate">{hoveredAsset.name}</p>
                      <p className="text-[10px] uppercase tracking-wider text-slate-500 mt-1">{hoveredAsset.folder || 'uncategorized'}</p>
                      <p className="text-[10px] text-slate-600 mt-1">Rê chuột để xem trước, click hoặc kéo để chèn vào canvas</p>
                    </div>
                  </div>
                </div>
              )}

              {loading ? (
                <div className="flex flex-col items-center justify-center py-20 gap-2 text-slate-500">
                  <Loader2 className="w-5 h-5 animate-spin text-indigo-500" />
                  <p className="text-xs">Đang tải...</p>
                </div>
              ) : assets.length === 0 ? (
                <div className="text-center py-16 text-slate-650 space-y-1">
                  <FolderOpen className="w-8 h-8 mx-auto text-slate-700" />
                  <p className="text-[11px] font-medium">Thư mục trống</p>
                </div>
              ) : (
                <div className="grid grid-cols-4 gap-2">
                  {assets.map((asset) => (
                    <div key={asset.id} className="flex flex-col space-y-1">
                      <div 
                        draggable={addingId !== asset.id}
                        onDragStart={handleAssetDragStart(asset)}
                        onMouseEnter={() => setHoveredAsset(asset)}
                        onMouseLeave={() => setHoveredAsset((current) => (current?.id === asset.id ? null : current))}
                        onClick={() => !addingId && addImageToCanvas(asset)}
                        className="group relative aspect-square bg-slate-950 rounded-xl overflow-hidden border border-slate-850 hover:border-indigo-500/50 cursor-grab active:cursor-grabbing transition-all p-1 flex items-center justify-center"
                      >
                        <img 
                          src={asset.file_url} 
                          alt={asset.name}
                          className="max-w-full max-h-full object-contain rounded-lg group-hover:scale-105 transition-transform duration-200"
                          loading="lazy"
                        />
                        <div className="absolute inset-0 bg-slate-950/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                          <div className="p-1 rounded-lg bg-indigo-600 text-white shadow-lg">
                            {addingId === asset.id ? (
                              <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            ) : (
                              <Plus className="w-3.5 h-3.5" />
                            )}
                          </div>
                        </div>
                      </div>
                      <span className="text-[9px] text-slate-400 text-center truncate px-0.5" title={asset.name}>
                        {asset.name}
                      </span>
                    </div>
                  ))}

                  {hasMore && (
                    <div className="col-span-4 flex justify-center mt-2 mb-4">
                      <button
                        type="button"
                        onClick={() => {
                          const nextPage = page + 1;
                          setPage(nextPage);
                          fetchAssets(nextPage, true);
                        }}
                        className="px-3 py-2 bg-slate-950 hover:bg-slate-900 border border-slate-800 text-[10px] font-bold rounded-xl text-slate-400 hover:text-white transition-all w-full flex items-center justify-center gap-1.5"
                      >
                        Tải thêm vật liệu
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}

        {/* TAB 2: SHAPES */}
        {activeTab === 'shapes' && (
          <div className="flex-1 overflow-y-auto pr-1 space-y-4">
            <p className="text-[10px] text-slate-500 px-1">Nhấp để chèn hình khối vector cơ bản:</p>
            <div className="grid grid-cols-2 gap-3">
              {shapesList.map((shape) => (
                <button
                  key={shape.type}
                  onClick={() => addShapeToCanvas(shape.type)}
                  className="h-24 bg-slate-950 border border-slate-850 hover:border-indigo-500/50 rounded-2xl flex flex-col items-center justify-center gap-1.5 hover:bg-slate-900/45 transition-all group"
                >
                  <span className="text-3xl text-indigo-400 group-hover:scale-110 transition-transform">{shape.icon}</span>
                  <span className="text-[10px] font-bold text-slate-400">{shape.name}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* TAB 3: STICKERS (ONLINE / EMOJI) */}
        {activeTab === 'stickers' && (
          <div className="flex-1 flex flex-col space-y-3 min-h-0">
            {/* Toggle Mode */}
            <div className="flex p-1 bg-slate-950 border border-slate-850 rounded-xl shrink-0">
              <button
                onClick={() => setStickerMode('online')}
                className={`flex-1 py-1 rounded-lg text-[10px] font-bold transition-all ${
                  stickerMode === 'online' ? 'bg-indigo-600 text-white shadow' : 'text-slate-500 hover:text-slate-300'
                }`}
              >
                Nhãn dán Online
              </button>
              <button
                onClick={() => setStickerMode('emoji')}
                className={`flex-1 py-1 rounded-lg text-[10px] font-bold transition-all ${
                  stickerMode === 'emoji' ? 'bg-indigo-600 text-white shadow' : 'text-slate-500 hover:text-slate-300'
                }`}
              >
                Vector Emojis
              </button>
            </div>

            {stickerMode === 'online' ? (
              /* ONLINE STICKERS GRID */
              <div className="flex-1 overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800">
                {loadingStickers ? (
                  <div className="flex flex-col items-center justify-center py-20 gap-2 text-slate-500">
                    <Loader2 className="w-5 h-5 animate-spin text-indigo-500" />
                    <p className="text-xs">Đang tải...</p>
                  </div>
                ) : onlineStickers.length === 0 ? (
                  <div className="text-center py-16 text-slate-650 space-y-1 select-none">
                    <Smile className="w-8 h-8 mx-auto text-slate-800" />
                    <p className="text-[11px] font-medium text-slate-400">Không có sticker online nào</p>
                    <p className="text-[9px] text-slate-600">Tải ảnh lên thư mục 'stickers' để cập nhật</p>
                  </div>
                ) : (
                  <div className="grid grid-cols-3 gap-2">
                    {onlineStickers.map((sticker) => (
                      <div 
                        key={sticker.id}
                        draggable={addingId !== sticker.id}
                        onDragStart={(e) => {
                          e.dataTransfer.effectAllowed = 'copy';
                          e.dataTransfer.setData('application/json', JSON.stringify(sticker));
                          e.dataTransfer.setData('text/plain', sticker.file_url);
                        }}
                        onClick={() => !addingId && addStickerToCanvas(sticker)}
                        className="group relative aspect-square bg-slate-950 rounded-xl overflow-hidden border border-slate-850 hover:border-indigo-500/50 cursor-grab active:cursor-grabbing transition-all p-1 flex items-center justify-center"
                      >
                        <img 
                          src={sticker.file_url} 
                          alt={sticker.name}
                          className="max-w-full max-h-full object-contain rounded-lg group-hover:scale-105 transition-transform duration-200"
                          loading="lazy"
                        />
                        <div className="absolute inset-0 bg-slate-950/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                          <div className="p-1 rounded-lg bg-indigo-600 text-white shadow-lg">
                            {addingId === sticker.id ? (
                              <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            ) : (
                              <Plus className="w-3.5 h-3.5" />
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              /* ORIGINAL OPENMOJI STICKERS GRID */
              <div className="flex-1 flex flex-col space-y-3 min-h-0">
                {/* Category tabs */}
                <div className="flex gap-1 overflow-x-auto py-1 no-scrollbar shrink-0 border-b border-slate-800/50 pb-2">
                  {emojiCategories.map(cat => (
                    <button
                      key={cat.name}
                      onClick={() => setActiveStickerCategory(cat.name)}
                      className={`px-2 py-0.5 rounded-md text-[10px] font-bold shrink-0 transition-all ${
                        activeStickerCategory === cat.name ? 'bg-indigo-600 text-white' : 'bg-slate-950 text-slate-500 hover:text-slate-300'
                      }`}
                    >
                      {cat.name}
                    </button>
                  ))}
                </div>

                {/* Grid */}
                <div className="flex-1 overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800">
                  <div className="grid grid-cols-4 gap-2">
                    {emojiCategories
                      .find(c => c.name === activeStickerCategory)
                      ?.emojis.map((emoji) => {
                        const hex = emojiToHex(emoji);
                        const url = `https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/svg/${hex}.svg`;
                        return (
                          <button
                            key={emoji}
                            onClick={() => !addingId && addEmojiSticker(emoji)}
                            disabled={addingId !== null}
                            className="aspect-square bg-slate-950 hover:bg-slate-900 border border-slate-850 hover:border-indigo-500/50 rounded-xl flex items-center justify-center relative p-1 group disabled:opacity-50"
                          >
                            {addingId === emoji ? (
                              <Loader2 className="w-4 h-4 animate-spin text-indigo-500" />
                            ) : (
                              <img 
                                src={url} 
                                alt={emoji}
                                className="w-8 h-8 object-contain group-hover:scale-110 transition-transform duration-200"
                                loading="lazy"
                              />
                            )}
                          </button>
                        );
                      })}
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* TAB 4: UNSPLASH BACKGROUNDS */}
        {activeTab === 'unsplash' && (
          <div className="flex-1 flex flex-col space-y-3 min-h-0">
            <p className="text-[10px] text-slate-500 px-1">Chèn hình nền hoạ tiết từ kho Unsplash CDN:</p>
            <div className="flex-1 overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800">
              <div className="grid grid-cols-2 gap-2">
                {unsplashBackgrounds.map((bg) => (
                  <div
                    key={bg.id}
                    onClick={() => !addingId && addBackgroundPhoto(bg.id, bg.name)}
                    className="group relative aspect-video bg-slate-950 rounded-xl overflow-hidden border border-slate-850 hover:border-indigo-500/50 cursor-pointer transition-all flex items-center justify-center"
                  >
                    <img 
                      src={`https://images.unsplash.com/${bg.id}?w=200&auto=format&fit=crop&q=80`} 
                      alt={bg.name}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-200"
                      loading="lazy"
                    />
                    <div className="absolute inset-x-0 bottom-0 bg-slate-950/70 py-1 px-2 text-center">
                      <p className="text-[9px] font-bold text-slate-300 truncate">{bg.name}</p>
                    </div>
                    <div className="absolute inset-0 bg-slate-950/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                      <div className="p-1 rounded-lg bg-indigo-600 text-white shadow-lg">
                        {addingId === bg.id ? (
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        ) : (
                          <Plus className="w-3.5 h-3.5" />
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* TAB 5: TEXT & TYPOGRAPHY PRESETS */}
        {activeTab === 'text' && (
          <div className="flex-1 flex flex-col space-y-4 min-h-0">
            <div className="flex-1 overflow-y-auto pr-1 scrollbar-thin scrollbar-thumb-slate-800 space-y-6">
              
              {/* Basic Texts */}
              <div className="space-y-2">
                <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider px-1">Cơ bản</p>
                <div className="flex flex-col gap-2">
                  {TYPOGRAPHY_PRESETS.filter(p => p.type === 'basic').map((preset) => (
                    <button
                      key={preset.id}
                      onClick={() => addTextToCanvas(preset)}
                      className="bg-slate-950 border border-slate-850 hover:border-indigo-500/50 hover:bg-indigo-500/10 rounded-xl p-3 flex items-center justify-center transition-all group cursor-pointer"
                    >
                      <span className="font-bold text-slate-200 group-hover:text-indigo-400 transition-colors" style={{ fontSize: preset.config.fontSize ? preset.config.fontSize / 5 : 16 }}>
                        {preset.preview}
                      </span>
                    </button>
                  ))}
                </div>
              </div>

              {/* Sale Texts */}
              <div className="space-y-2">
                <p className="text-[10px] font-bold text-rose-500 uppercase tracking-wider px-1">Bán hàng / Khuyến mãi</p>
                <div className="grid grid-cols-2 gap-2">
                  {TYPOGRAPHY_PRESETS.filter(p => p.type === 'sale').map((preset) => (
                    <button
                      key={preset.id}
                      onClick={() => addTextToCanvas(preset)}
                      className="bg-slate-950 border border-slate-850 hover:border-rose-500/50 hover:bg-rose-500/10 rounded-xl h-20 flex items-center justify-center transition-all group cursor-pointer overflow-hidden"
                    >
                      <span 
                        className="font-black italic tracking-tight"
                        style={{ 
                          color: preset.config.fill as string,
                          WebkitTextStroke: preset.config.strokeWidth ? `${preset.config.strokeWidth / 4}px ${preset.config.stroke}` : undefined,
                          textShadow: preset.config.shadow ? `${preset.config.shadow.offsetX / 4}px ${preset.config.shadow.offsetY / 4}px ${preset.config.shadow.blur}px ${preset.config.shadow.color}` : undefined,
                          fontSize: preset.config.fontSize ? preset.config.fontSize / 7 : 16
                        }}
                      >
                        {preset.preview}
                      </span>
                    </button>
                  ))}
                </div>
              </div>

              {/* Artistic Texts */}
              <div className="space-y-2 pb-4">
                <p className="text-[10px] font-bold text-sky-500 uppercase tracking-wider px-1">Nghệ thuật</p>
                <div className="grid grid-cols-2 gap-2">
                  {TYPOGRAPHY_PRESETS.filter(p => p.type === 'artistic').map((preset) => (
                    <button
                      key={preset.id}
                      onClick={() => addTextToCanvas(preset)}
                      className="bg-slate-950 border border-slate-850 hover:border-sky-500/50 hover:bg-sky-500/10 rounded-xl h-20 flex items-center justify-center transition-all group cursor-pointer overflow-hidden"
                    >
                      <span 
                        className="font-bold tracking-tight"
                        style={{ 
                          color: preset.config.fill === 'transparent' ? 'transparent' : (preset.config.fill as string),
                          fontFamily: preset.config.fontFamily || 'inherit',
                          WebkitTextStroke: preset.config.strokeWidth ? `${preset.config.strokeWidth / 4}px ${preset.config.stroke}` : undefined,
                          textShadow: preset.config.shadow ? `${preset.config.shadow.offsetX / 4}px ${preset.config.shadow.offsetY / 4}px ${preset.config.shadow.blur}px ${preset.config.shadow.color}` : undefined,
                          fontSize: preset.config.fontSize ? preset.config.fontSize / 7 : 16
                        }}
                      >
                        {preset.preview}
                      </span>
                    </button>
                  ))}
                </div>
              </div>

            </div>
          </div>
        )}

      </div>
    </div>
  );
}
