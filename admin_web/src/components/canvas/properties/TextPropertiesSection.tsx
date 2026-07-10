'use client';

import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  Bold,
  Italic,
  Underline,
  Strikethrough,
  AlignLeft,
  AlignCenter,
  AlignRight,
  ChevronDown,
  Trash2,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { toast } from 'sonner';
import { CollapsibleSection } from './CollapsibleSection';
import { rgbaToHex } from './color-utils';
import { resolveLayerType } from '@/lib/canvas-object-props';
import {
  fetchFontsManifest,
  filterFonts,
  FONT_CATEGORIES,
  groupFontsByStyle,
  injectManifestFontFaces,
} from '@/lib/fonts-manifest';
import type { FontManifestEntry } from '@/domains/fonts/font.types';
import { useEditorStore } from '@/store/editor.store';
import { useLayersStore } from '@/store/layers.store';

export type TextPropertySection =
  | 'all'
  | 'font'
  | 'size'
  | 'style'
  | 'format'
  | 'align'
  | 'bg';

interface TextPropertiesSectionProps {
  showContent: boolean;
  activeObjectProps: Record<string, any>;
  onPropChange: (name: string, value: any) => void;
  onRecordChange: () => void;
  /** Phase 2: show only one tab slice for LabelToolPanel */
  section?: TextPropertySection;
  /** Skip CollapsibleSection wrapper (context panel tabs) */
  flat?: boolean;
}

export function TextPropertiesSection({
  showContent,
  activeObjectProps,
  onPropChange,
  onRecordChange,
  section = 'all',
  flat = false,
}: TextPropertiesSectionProps) {
  const { canvas } = useEditorStore();
  const { updateActiveObject } = useLayersStore();

  const [isFontDropdownOpen, setIsFontDropdownOpen] = useState(false);
  const [fontSearchQuery, setFontSearchQuery] = useState('');
  const [fontDropdownRect, setFontDropdownRect] = useState<{
    top: number;
    left: number;
    width: number;
  } | null>(null);
  const fontTriggerRef = useRef<HTMLButtonElement>(null);
  const [manifestFonts, setManifestFonts] = useState<FontManifestEntry[]>([]);
  const [customPresets, setCustomPresets] = useState<
    {
      id: string;
      name: string;
      fontFamily: string;
      fontSize: number;
      fontWeight: string;
      fontStyle: string;
      fill: string;
    }[]
  >([]);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const stored = localStorage.getItem('custom_typography_presets');
      if (stored) {
        try {
          setCustomPresets(JSON.parse(stored));
        } catch {
          console.warn('Failed to parse custom typography presets');
        }
      }
    }
  }, []);

  useEffect(() => {
    const loadManifest = async () => {
      try {
        const manifest = await fetchFontsManifest();
        setManifestFonts(manifest.fonts);
        injectManifestFontFaces(manifest.fonts);
      } catch (err) {
        console.error('Failed to load fonts manifest:', err);
      }
    };
    loadManifest();
  }, []);

  useEffect(() => {
    if (!isFontDropdownOpen) return;
    const handleOutsideClick = (e: MouseEvent) => {
      const container = document.getElementById('font-family-selector-container');
      const dropdown = document.getElementById('font-family-dropdown-portal');
      const target = e.target as Node;
      if (
        container &&
        !container.contains(target) &&
        (!dropdown || !dropdown.contains(target))
      ) {
        setIsFontDropdownOpen(false);
        setFontSearchQuery('');
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, [isFontDropdownOpen]);

  const updateFontDropdownRect = () => {
    if (!fontTriggerRef.current) return;
    const rect = fontTriggerRef.current.getBoundingClientRect();
    setFontDropdownRect({
      top: rect.bottom + 4,
      left: rect.left,
      width: rect.width,
    });
  };

  const toggleFontDropdown = () => {
    const next = !isFontDropdownOpen;
    setIsFontDropdownOpen(next);
    if (next) {
      updateFontDropdownRect();
    } else {
      setFontDropdownRect(null);
      setFontSearchQuery('');
    }
  };

  useLayoutEffect(() => {
    if (!isFontDropdownOpen) return;
    updateFontDropdownRect();
    const onReposition = () => updateFontDropdownRect();
    window.addEventListener('resize', onReposition);
    window.addEventListener('scroll', onReposition, true);
    return () => {
      window.removeEventListener('resize', onReposition);
      window.removeEventListener('scroll', onReposition, true);
    };
  }, [isFontDropdownOpen]);

  const saveCurrentAsPreset = () => {
    const name = prompt('Nhập tên cho Preset của bạn:', `Preset ${customPresets.length + 1}`);
    if (!name) return;
    const newPreset = {
      id: `preset_${Date.now()}`,
      name,
      fontFamily: activeObjectProps.fontFamily || 'Outfit',
      fontSize: activeObjectProps.fontSize || 32,
      fontWeight: activeObjectProps.fontWeight || 'normal',
      fontStyle: activeObjectProps.fontStyle || 'normal',
      fill: activeObjectProps.fill || '#6366f1',
    };
    const updated = [...customPresets, newPreset];
    setCustomPresets(updated);
    localStorage.setItem('custom_typography_presets', JSON.stringify(updated));
    toast.success('Đã lưu mẫu chữ của bạn thành công!');
  };

  const deleteCustomPreset = (id: string) => {
    const updated = customPresets.filter((p) => p.id !== id);
    setCustomPresets(updated);
    localStorage.setItem('custom_typography_presets', JSON.stringify(updated));
    toast.success('Đã xóa mẫu chữ!');
  };

  const handleTextTransformToggle = () => {
    if (!canvas) return;
    const activeObject = canvas.getActiveObject() as any;
    if (!activeObject || (activeObject.type !== 'i-text' && activeObject.layerType !== 'TEXT')) return;
    const currentTransform = activeObject.textTransform || 'none';
    const newTransform = currentTransform === 'uppercase' ? 'none' : 'uppercase';
    const originalText = activeObject._originalText || activeObject.text;
    if (newTransform === 'uppercase') {
      updateActiveObject({
        text: originalText.toUpperCase(),
        textTransform: 'uppercase',
        _originalText: originalText,
      });
    } else {
      updateActiveObject({ text: originalText, textTransform: 'none' });
    }
    onRecordChange();
  };

  const numberedFonts = manifestFonts.map((f, index) => ({ ...f, seq: index + 1 }));

  const filteredFonts = filterFonts(numberedFonts, fontSearchQuery);

  const groupedFonts = groupFontsByStyle(filteredFonts);

  const activeFontEntry = numberedFonts.find((f) => f.family_slug === activeObjectProps.fontFamily);

  const activeObj = canvas?.getActiveObject();
  const isTextLayer =
    activeObjectProps.layerType === 'TEXT' ||
    (activeObj ? resolveLayerType(activeObj) === 'TEXT' : false);

  if (!showContent || !isTextLayer) return null;

  const show = (part: TextPropertySection) => section === 'all' || section === part;

  const inner = (
    <>
      {show('all') && (
      <div className="space-y-1">
        <span className="text-[9px] font-semibold text-slate-400">Nội dung văn bản</span>
        <textarea
          value={activeObjectProps.text || ''}
          onChange={(e) => onPropChange('text', e.target.value)}
          rows={2}
          className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-700 focus:outline-none focus:border-indigo-600 resize-none font-sans"
          placeholder="Nhập nội dung chữ..."
        />
      </div>
      )}
      {show('style') && (
      <div className="space-y-1.5">
        <span className="text-[9px] font-semibold text-slate-400">Định dạng</span>
        <div className="grid grid-cols-5 gap-1.5">
          <Button
            size="sm"
            variant="outline"
            onClick={() =>
              onPropChange('fontWeight', activeObjectProps.fontWeight === 'bold' ? 'normal' : 'bold')
            }
            className={`rounded-xl h-8 border-slate-200 cursor-pointer p-0 ${
              activeObjectProps.fontWeight === 'bold'
                ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30'
                : 'bg-white text-slate-500'
            }`}
          >
            <Bold className="w-3.5 h-3.5" />
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() =>
              onPropChange('fontStyle', activeObjectProps.fontStyle === 'italic' ? 'normal' : 'italic')
            }
            className={`rounded-xl h-8 border-slate-200 cursor-pointer p-0 ${
              activeObjectProps.fontStyle === 'italic'
                ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30'
                : 'bg-white text-slate-500'
            }`}
          >
            <Italic className="w-3.5 h-3.5" />
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => onPropChange('underline', !activeObjectProps.underline)}
            className={`rounded-xl h-8 border-slate-200 cursor-pointer p-0 ${
              activeObjectProps.underline
                ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30'
                : 'bg-white text-slate-500'
            }`}
          >
            <Underline className="w-3.5 h-3.5" />
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => onPropChange('linethrough', !activeObjectProps.linethrough)}
            className={`rounded-xl h-8 border-slate-200 cursor-pointer p-0 ${
              activeObjectProps.linethrough
                ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30'
                : 'bg-white text-slate-500'
            }`}
          >
            <Strikethrough className="w-3.5 h-3.5" />
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={handleTextTransformToggle}
            className={`rounded-xl h-8 border-slate-200 cursor-pointer p-0 ${
              activeObjectProps.textTransform === 'uppercase'
                ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30'
                : 'bg-white text-slate-500'
            }`}
          >
            <span className="text-[10px] tracking-tighter">Aa</span>
          </Button>
        </div>
      </div>
      )}
      {show('align') && (
      <div className="space-y-1.5">
        <span className="text-[9px] font-semibold text-slate-400">Căn lề</span>
        <div className="flex gap-1.5">
          {(['left', 'center', 'right'] as const).map((a) => (
            <Button
              key={a}
              size="sm"
              variant="outline"
              onClick={() => onPropChange('textAlign', a)}
              className={`flex-1 rounded-xl h-8 border-slate-200 cursor-pointer ${
                activeObjectProps.textAlign === a
                  ? 'bg-indigo-600/10 text-indigo-600 border-indigo-500/30'
                  : 'bg-white text-slate-500'
              }`}
            >
              {a === 'left' ? (
                <AlignLeft className="w-3.5 h-3.5" />
              ) : a === 'center' ? (
                <AlignCenter className="w-3.5 h-3.5" />
              ) : (
                <AlignRight className="w-3.5 h-3.5" />
              )}
            </Button>
          ))}
        </div>
      </div>
      )}
      {show('size') && (
      <div className="space-y-1">
        <span className="text-[9px] font-semibold text-slate-400">Font Size</span>
        <Input
          type="number"
          value={activeObjectProps.fontSize || 40}
          onChange={(e) => onPropChange('fontSize', parseInt(e.target.value) || 12)}
          className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8"
        />
      </div>
      )}
      {show('format') && (
      <>
      <div className="space-y-1">
        <div className="flex justify-between text-[9px] font-semibold text-slate-400">
          <span>Line Height</span>
          <span>
            {activeObjectProps.lineHeight !== undefined ? activeObjectProps.lineHeight.toFixed(2) : '1.16'}
          </span>
        </div>
        <input
          type="range"
          min="0.8"
          max="3.0"
          step="0.05"
          value={activeObjectProps.lineHeight !== undefined ? activeObjectProps.lineHeight : 1.16}
          onChange={(e) => onPropChange('lineHeight', parseFloat(e.target.value))}
          className="w-full h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600"
        />
      </div>
      <div className="space-y-1">
        <div className="flex justify-between text-[9px] font-semibold text-slate-400">
          <span>Letter Spacing</span>
          <span>{activeObjectProps.charSpacing !== undefined ? activeObjectProps.charSpacing : '0'}</span>
        </div>
        <input
          type="range"
          min="-50"
          max="200"
          step="5"
          value={activeObjectProps.charSpacing !== undefined ? activeObjectProps.charSpacing : 0}
          onChange={(e) => onPropChange('charSpacing', parseInt(e.target.value))}
          className="w-full h-1 bg-white rounded-lg appearance-none cursor-pointer accent-indigo-600"
        />
      </div>
      </>
      )}
      {show('font') && (
      <div className="space-y-1 relative" id="font-family-selector-container">
        <span className="text-[9px] font-semibold text-slate-400">Font Family</span>
        <button
          ref={fontTriggerRef}
          type="button"
          onClick={toggleFontDropdown}
          className="w-full bg-white border border-slate-200 rounded-xl px-3 py-2.5 text-xs text-slate-400 focus:outline-none focus:border-indigo-600 flex items-center justify-between hover:bg-white cursor-pointer"
          style={{ fontFamily: activeObjectProps.fontFamily || 'sans-serif' }}
        >
          <span className="truncate text-sm">
            {activeFontEntry?.seq
              ? `#${String(activeFontEntry.seq).padStart(2, '0')}. `
              : ''}
            {activeFontEntry?.name ||
              activeObjectProps.fontFamily ||
              'Outfit'}
          </span>
          <ChevronDown
            className={`w-3.5 h-3.5 text-slate-500 transition-transform duration-200 ${
              isFontDropdownOpen ? 'rotate-180' : ''
            }`}
          />
        </button>
        {isFontDropdownOpen &&
          fontDropdownRect &&
          typeof document !== 'undefined' &&
          createPortal(
            <div
              id="font-family-dropdown-portal"
              className="fixed z-[9999] bg-white border border-slate-200 rounded-xl shadow-xl flex flex-col max-h-[360px] overflow-hidden animate-in fade-in slide-in-from-top-1 duration-150"
              style={{
                top: fontDropdownRect.top,
                left: fontDropdownRect.left,
                width: fontDropdownRect.width,
              }}
            >
              <div className="p-2 border-b border-slate-200/80 sticky top-0 bg-white z-10">
                <input
                  type="text"
                  placeholder="Tìm kiếm font..."
                  value={fontSearchQuery}
                  onChange={(e) => setFontSearchQuery(e.target.value)}
                  className="w-full bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs text-slate-400 focus:outline-none focus:border-indigo-600 font-sans"
                  autoFocus
                />
              </div>
              <div className="flex-1 overflow-y-auto divide-y divide-slate-900 scrollbar-thin scrollbar-thumb-slate-300">
                {Object.keys(groupedFonts).length === 0 ? (
                  <div className="p-4 text-center text-xs text-slate-400 font-sans">
                    Không tìm thấy font nào
                  </div>
                ) : (
                  (FONT_CATEGORIES.filter((cat) => groupedFonts[cat] && groupedFonts[cat].length > 0) as any[])
                    .concat(
                      Object.keys(groupedFonts).filter(
                        (cat) => !FONT_CATEGORIES.includes(cat as any) && groupedFonts[cat].length > 0
                      )
                    )
                    .map((category) => (
                      <div key={category} className="bg-white">
                        <div className="bg-white/60 text-slate-500 font-bold uppercase text-[9px] tracking-wider py-1 px-3 border-y border-slate-200/40 sticky top-0 backdrop-blur-md font-sans">
                          {category}
                        </div>
                        <div className="py-1">
                          {groupedFonts[category].map((f) => (
                            <button
                              key={f.family_slug}
                              type="button"
                              onClick={() => {
                                onPropChange('fontFamily', f.family_slug);
                                setIsFontDropdownOpen(false);
                                setFontSearchQuery('');
                                setFontDropdownRect(null);
                              }}
                              className={`w-full text-left px-3 py-2 hover:bg-indigo-600/10 transition-colors flex items-center justify-between cursor-pointer group ${
                                activeObjectProps.fontFamily === f.family_slug
                                  ? 'bg-indigo-600/5 border-l-2 border-indigo-500'
                                  : ''
                              }`}
                            >
                              <div className="flex flex-col min-w-0">
                                <div className="flex items-center gap-1.5 text-[10px] text-slate-400 group-hover:text-indigo-600 font-sans">
                                  <span className="font-mono font-bold">
                                    #{String(f.seq).padStart(2, '0')}
                                  </span>
                                  <span>•</span>
                                  <span className="truncate">{f.name}</span>
                                </div>
                                <span
                                  className="text-base text-slate-400 group-hover:text-slate-800 truncate pt-0.5"
                                  style={{ fontFamily: f.family_slug }}
                                >
                                  {f.name}
                                </span>
                              </div>
                              {activeObjectProps.fontFamily === f.family_slug && (
                                <span className="text-xs text-indigo-600 font-bold font-sans">✓</span>
                              )}
                            </button>
                          ))}
                        </div>
                      </div>
                    ))
                )}
              </div>
            </div>,
            document.body
          )}
      </div>
      )}
      {show('bg') && (
      <div className="space-y-1 pt-2 border-t border-slate-200/30">
        <span className="text-[9px] font-semibold text-slate-400">Màu nền chữ</span>
        <div className="flex gap-2">
          <input
            type="color"
            value={rgbaToHex(activeObjectProps.textBackgroundColor || 'rgba(0,0,0,0)')}
            onChange={(e) => onPropChange('textBackgroundColor', e.target.value)}
            className="w-8 h-8 rounded-lg bg-transparent border border-slate-200 cursor-pointer overflow-hidden"
          />
          <Input
            value={activeObjectProps.textBackgroundColor || ''}
            onChange={(e) => onPropChange('textBackgroundColor', e.target.value)}
            className="bg-white border-slate-200 text-xs text-slate-400 rounded-xl h-8 font-mono flex-1"
            placeholder="Không màu nền"
          />
          {activeObjectProps.textBackgroundColor && (
            <Button
              size="sm"
              variant="destructive"
              onClick={() => onPropChange('textBackgroundColor', null)}
              className="h-8 rounded-xl px-2.5 cursor-pointer text-xs"
            >
              Xóa
            </Button>
          )}
        </div>
      </div>
      )}
      {show('style') && (
      <div className="space-y-2 pt-2 border-t border-slate-200/30">
        <span className="text-[9px] font-semibold text-slate-400 block">Mẫu chữ nhanh (Presets)</span>
        <div className="grid grid-cols-2 gap-1.5">
          {[
            { name: 'Tiêu đề chính', font: 'Outfit', size: 90, weight: 'bold' },
            { name: 'Tiêu đề phụ', font: 'Outfit', size: 45, weight: '600' },
            { name: 'Văn bản thân', font: 'Outfit', size: 24, weight: 'normal' },
            { name: 'Chú thích', font: 'Outfit', size: 16, weight: 'normal' },
          ].map((p, idx) => (
            <Button
              key={idx}
              type="button"
              size="sm"
              variant="outline"
              onClick={() => {
                onPropChange('fontFamily', p.font);
                onPropChange('fontSize', p.size);
                onPropChange('fontWeight', p.weight);
              }}
              className="bg-white border-slate-200 text-[10px] py-1 h-7 rounded-xl text-slate-500 hover:text-slate-800 cursor-pointer hover:bg-white"
            >
              {p.name}
            </Button>
          ))}
        </div>
        {customPresets.length > 0 && (
          <div className="space-y-1.5 pt-1.5">
            <span className="text-[8px] font-semibold text-slate-400 block">Mẫu đã lưu</span>
            <div className="space-y-1">
              {customPresets.map((p) => (
                <div key={p.id} className="flex items-center gap-1.5">
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      onPropChange('fontFamily', p.fontFamily);
                      onPropChange('fontSize', p.fontSize);
                      onPropChange('fontWeight', p.fontWeight);
                      onPropChange('fontStyle', p.fontStyle);
                      onPropChange('fill', p.fill);
                    }}
                    className="flex-1 bg-white border-slate-200 text-[10px] text-left justify-start px-2.5 h-7 rounded-xl text-slate-400 hover:text-slate-800 cursor-pointer truncate"
                  >
                    {p.name}
                  </Button>
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    onClick={() => deleteCustomPreset(p.id)}
                    className="w-7 h-7 text-rose-500 hover:bg-rose-500/10 rounded-xl"
                    title="Xóa mẫu chữ này"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </Button>
                </div>
              ))}
            </div>
          </div>
        )}
        <Button
          type="button"
          size="sm"
          onClick={saveCurrentAsPreset}
          className="w-full bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-[10px] font-bold h-7.5 mt-1 cursor-pointer"
        >
          + Lưu định dạng hiện tại làm mẫu
        </Button>
      </div>
      )}
      {show('all') && (
      <div className="flex items-center justify-between pt-2 border-t border-slate-200/30">
        <div className="flex flex-col">
          <span className="text-[10px] font-semibold text-slate-400">Co chữ vừa khung</span>
          <span className="text-[8px] text-slate-400">Giữ chữ nằm gọn trong khung viền</span>
        </div>
        <input
          type="checkbox"
          checked={!!activeObjectProps.autoFit}
          onChange={(e) => {
            const checked = e.target.checked;
            if (checked) {
              const canvasInstance = canvas;
              const activeObj = canvasInstance?.getActiveObject();
              if (activeObj) {
                const h = activeObj.height * activeObj.scaleY;
                onPropChange('autoFit', true);
                onPropChange('maxHeight', h);
              }
            } else {
              onPropChange('autoFit', false);
              onPropChange('maxHeight', null);
            }
          }}
          className="rounded text-indigo-600 focus:ring-indigo-600 bg-white border-slate-200 cursor-pointer"
        />
      </div>
      )}
    </>
  );

  if (flat) {
    return <div className="space-y-3">{inner}</div>;
  }

  return (
    <CollapsibleSection
      id="text-style"
      icon={<span className="text-[10px]">🔤</span>}
      title="Kiểu chữ"
      defaultOpen={true}
    >
      {inner}
    </CollapsibleSection>
  );
}
