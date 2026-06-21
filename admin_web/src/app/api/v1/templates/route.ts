import { NextRequest, NextResponse } from 'next/server';
import { createSupabaseAdmin } from '@/lib/supabase';
import { removeCDN } from '@/lib/cdn-rewriter';
import { resolveCategoryNames } from '@/domains/categories/category-slug-map';

export const dynamic = 'force-dynamic';

function colorToArgbInt(colorString: string): number | null {
  if (!colorString) return null;
  let r = 255;
  let g = 255;
  let b = 255;
  let a = 1;

  if (colorString.startsWith('rgba') || colorString.startsWith('rgb')) {
    const match = colorString.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
    if (!match) return null;
    r = Number(match[1]);
    g = Number(match[2]);
    b = Number(match[3]);
    a = match[4] !== undefined ? Number(match[4]) : 1;
  } else if (colorString.startsWith('#')) {
    const hex = colorString.replace('#', '');
    if (hex.length === 3) {
      r = parseInt(hex[0] + hex[0], 16);
      g = parseInt(hex[1] + hex[1], 16);
      b = parseInt(hex[2] + hex[2], 16);
    } else if (hex.length === 6 || hex.length === 8) {
      r = parseInt(hex.slice(0, 2), 16);
      g = parseInt(hex.slice(2, 4), 16);
      b = parseInt(hex.slice(4, 6), 16);
      if (hex.length === 8) a = parseInt(hex.slice(6, 8), 16) / 255;
    } else {
      return null;
    }
  } else {
    return null;
  }

  return ((Math.round(a * 255) << 24) | (r << 16) | (g << 8) | b);
}

function extractBackgroundColorArgb(fabricState: any): number | null {
  const bg = fabricState?.backgroundColor || fabricState?.background;
  if (!bg) return null;
  if (typeof bg === 'string') return colorToArgbInt(bg);
  const firstStop = bg.colorStops?.[0]?.color;
  return typeof firstStop === 'string' ? colorToArgbInt(firstStop) : null;
}

function enrichCanvasDataFromFabricState(row: any) {
  const canvasData = row?.canvas_data;
  if (!canvasData?.canvas) {
    return row;
  }
  if (canvasData.canvas.backgroundColorArgb !== undefined && canvasData.canvas.backgroundColorArgb !== null) {
    return row;
  }

  const backgroundColorArgb = extractBackgroundColorArgb(row.fabric_state);
  if (backgroundColorArgb === null) {
    return row;
  }

  return {
    ...row,
    canvas_data: {
      ...canvasData,
      canvas: {
        ...canvasData.canvas,
        backgroundColorArgb,
      },
    },
  };
}

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const search = searchParams.get('search')?.trim() || '';
    const categoryId = searchParams.get('categoryId')?.trim() || '';
    const templateId = searchParams.get('templateId')?.trim() || '';
    const env = searchParams.get('env')?.trim() || '';
    const limitParam = Number(searchParams.get('limit') || '100');
    const limit = Number.isFinite(limitParam) ? Math.min(Math.max(limitParam, 1), 200) : 100;

    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(categoryId);
    const selectQuery = categoryId && !isUuid
      ? 'id, template_id, category_id, title, status, environment, thumbnail_url, canvas_data, fabric_state, created_at, updated_at, categories!inner(id, name)'
      : 'id, template_id, category_id, title, status, environment, thumbnail_url, canvas_data, fabric_state, created_at, updated_at, categories(id, name)';

    const supabase = createSupabaseAdmin();
    let query = supabase
      .from('templates')
      .select(selectQuery);

    if (templateId) {
      const isTemplateIdUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(templateId);
      if (isTemplateIdUuid) {
        query = query.or(`id.eq.${templateId},template_id.eq.${templateId}`);
      } else {
        query = query.eq('template_id', templateId);
      }
    } else {
      query = query.eq('status', 'published');

      if (env) {
        query = query.in('environment', [env, 'all']);
      }

      if (search) {
        query = query.ilike('title', `%${search}%`);
      }

      if (categoryId) {
        if (isUuid) {
          query = query.eq('category_id', categoryId);
        } else {
          const categoryNames = resolveCategoryNames(categoryId);
          query = query.in('categories.name', categoryNames);
        }
      }
    }

    let { data, error } = await query
      .order('updated_at', { ascending: false })
      .limit(limit);

    if (error) {
      if (error.code === '42703') {
        console.warn('Fallback: environment column does not exist in DB yet. Retrying without environment filter.');
        const fallbackSelectQuery = categoryId && !isUuid
          ? 'id, template_id, category_id, title, status, thumbnail_url, canvas_data, fabric_state, created_at, updated_at, categories!inner(id, name)'
          : 'id, template_id, category_id, title, status, thumbnail_url, canvas_data, fabric_state, created_at, updated_at, categories(id, name)';

        let fallbackQuery = supabase
          .from('templates')
          .select(fallbackSelectQuery);

        if (templateId) {
          const isTemplateIdUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(templateId);
          if (isTemplateIdUuid) {
            fallbackQuery = fallbackQuery.or(`id.eq.${templateId},template_id.eq.${templateId}`);
          } else {
            fallbackQuery = fallbackQuery.eq('template_id', templateId);
          }
        } else {
          fallbackQuery = fallbackQuery.eq('status', 'published');
          if (search) {
            fallbackQuery = fallbackQuery.ilike('title', `%${search}%`);
          }
          if (categoryId) {
            if (isUuid) {
              fallbackQuery = fallbackQuery.eq('category_id', categoryId);
            } else {
              const categoryNames = resolveCategoryNames(categoryId);
              fallbackQuery = fallbackQuery.in('categories.name', categoryNames);
            }
          }
        }

        const fallbackResult = await fallbackQuery
          .order('updated_at', { ascending: false })
          .limit(limit);

        if (fallbackResult.error) throw fallbackResult.error;
        data = fallbackResult.data as any;
      } else {
        throw error;
      }
    }

    const rawTemplates = removeCDN((data || []).map(enrichCanvasDataFromFabricState));

    // Strip unused fabric_state and conditionally strip layers for list views
    const isDetailFetch = !!templateId;
    const templates = rawTemplates.map((tpl: any) => {
      if (tpl && typeof tpl === 'object') {
        // Exclude fabric_state entirely (not used by Android App)
        delete tpl.fabric_state;

        // Exclude layers from canvas_data if it's a listing view to save bandwidth
        if (!isDetailFetch && tpl.canvas_data) {
          const { layers, ...lightCanvasData } = tpl.canvas_data;
          tpl.canvas_data = lightCanvasData;
        }
      }
      return tpl;
    });

    return NextResponse.json(
      { success: true, templates },
      { headers: { 'Cache-Control': 'no-store' } }
    );
  } catch (error: unknown) {
    console.error('Error fetching public templates:', error);
    const message = error instanceof Error ? error.message : 'Failed to fetch public templates';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
