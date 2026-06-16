export interface CloudCategory {
  id: string;
  name: string;
  order: number;
}

export interface CreateCategoryInput {
  name: string;
  order?: number;
}

export interface UpdateCategoryInput {
  name?: string;
  order?: number;
}
