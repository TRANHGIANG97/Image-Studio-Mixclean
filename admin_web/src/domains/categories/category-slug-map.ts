const CATEGORY_SLUG_NAMES: Record<string, string[]> = {
  professional: ['Thời trang', 'Chuyên nghiệp'],
  cosmetics: ['Mỹ Phẩm', 'Mẫu Mỹ Phẩm'],
  digital_life: ['Đời sống số'],
  selfie_food: ['Mê ăn uống'],
};

export function resolveCategoryNames(slugOrName: string): string[] {
  const mapped = CATEGORY_SLUG_NAMES[slugOrName.toLowerCase()];
  return mapped ?? [slugOrName];
}
