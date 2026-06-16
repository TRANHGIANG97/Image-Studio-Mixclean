export interface AssetRecord {
  id: string;
  name: string;
  folder: string;
  source_path: string;
  file_url: string;
  file_size: number;
  width: number;
  height: number;
  mime_type: string;
  created_at: string;
}

export interface UploadAssetInput {
  name: string;
  folder: string;
  file: File;
}
