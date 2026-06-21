export interface TemplateMetadata {
  title: string;
  thumbnailUrl: string;
  status: 'draft' | 'published';
  environment?: 'debug' | 'release' | 'all';
  schemaVersion: number;
  createdAt: number;
  updatedAt: number;
}

export interface TemplateCanvas {
  baseWidth: number;
  baseHeight: number;
  aspectRatio: string; // e.g. "9:16", "1:1", "4:5"
  backgroundUrl: string | null;
  backgroundColorArgb?: number | null;
}

export interface CloudTransform {
  // Relative coordinates (0.0 to 1.0) of the layer center on the canvas
  anchorX: number;
  anchorY: number;
  scale: number;
  rotation: number; // in degrees, e.g. 0 to 360
}

export interface CloudPayload {
  defaultImageUrl?: string | null;
  imageUrl?: string | null;
  visible?: boolean | null;
  locked?: boolean | null;
  groupPath?: string | null;
  sourceKind?: string | null;
  shadowIntensity?: number | null;
  shadowAngle?: number | null;
  shadowDistance?: number | null;
  alpha?: number | null;
  shadowColorArgb?: number | null;
  shadowBlur?: number | null;
  cropRatio?: string | null;
  flippedH?: boolean | null;
  flippedV?: boolean | null;
  baseWidth?: number | null;
  baseHeight?: number | null;
  text?: string | null;
  font?: string | null;
  textColorArgb?: number | null;
  fontSize?: number | null;
  fill?: string | null;
  fontWeight?: string | number | null;
  fontStyle?: string | null;
  textAlign?: string | null;
  underline?: boolean | null;
  lineHeight?: number | null;
  charSpacing?: number | null;
  textBackgroundColor?: string | null;
  linethrough?: boolean | null;
  textTransform?: string | null;
  
  // Vector Shape properties
  shapeType?: string | null;
  fillColor?: string | null;
  rx?: number | null;
  ry?: number | null;
  blendMode?: string | null;
  pathData?: string | null;
  polygonPoints?: number[] | null;
  /** When true, Android studio_edit shows the Replace button on this layer. */
  replaceable?: boolean | null;
}

export interface CloudLayer {
  layerId: string;
  type: 'IMAGE' | 'DECORATION' | 'TEXT' | string; // maps to IMAGE, DECORATION, TEXT
  zIndex: number;
  transform: CloudTransform;
  payload: CloudPayload;
}

export interface CloudTemplate {
  templateId: string;
  categoryId: string;
  metadata: TemplateMetadata;
  canvas: TemplateCanvas;
  layers: CloudLayer[];
}
