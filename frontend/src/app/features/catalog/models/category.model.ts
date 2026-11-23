// Category shapes, mirroring catalog/dto/CategoryDtos.java in the backend.

/** One step of a category breadcrumb. */
export interface CategoryRef {
  id: number;
  name: string;
  slug: string;
}

/** Node of the 3-level tree from GET /api/categories (roots, groups, leaves). Products sit in leaves. */
export interface CategoryNode extends CategoryRef {
  level: number;
  leaf: boolean;
  children: CategoryNode[];
}
