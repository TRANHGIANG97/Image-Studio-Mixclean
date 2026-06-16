import { create } from 'zustand';

export type CropRequest = {
  imageUrl: string;
  sourceName: string;
  initialRatio?: string;
};

interface CropState {
  request: CropRequest | null;
  openCrop: (request: CropRequest) => void;
  closeCrop: () => void;
}

export const useCropStore = create<CropState>((set) => ({
  request: null,
  openCrop: (request) => set({ request }),
  closeCrop: () => set({ request: null }),
}));
