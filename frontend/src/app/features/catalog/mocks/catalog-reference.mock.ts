// Mock category tree and brands until /api/categories and /api/brands are wired.
// A subset of the seeded taxonomy (12 roots, 33 groups, 87 leaves in the backend).
import { PageResponse, Ref } from '../../../shared/models/api.model';
import { CategoryNode } from '../models/category.model';

type Tree = [number, string, Tree[]?];

const slug = (name: string) => name.toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]+/g, '-').replace(/-$/, '');

function node([id, name, children]: Tree, level: number): CategoryNode {
  const kids = (children ?? []).map((c) => node(c, level + 1));
  return { id, name, slug: slug(name), level, leaf: kids.length === 0, children: kids };
}

const TREE: Tree[] = [
  [1, 'Electronics', [
    [12, 'Computers', [[104, 'Laptops'], [105, 'Desktops'], [106, 'Monitors'], [107, 'Tablets']]],
    [13, 'Audio', [[110, 'Headphones'], [111, 'Speakers'], [112, 'Soundbars']]],
    [14, 'Accessories', [[115, 'Mice & Keyboards'], [116, 'Chargers & Cables']]],
  ]],
  [2, 'Clothing', [
    [20, "Men's", [[120, 'Shirts'], [121, 'Pants'], [122, 'Sweaters']]],
    [21, "Women's", [[125, 'Dresses'], [126, 'Tops'], [127, 'Jeans']]],
  ]],
  [3, 'Home & Kitchen', [
    [30, 'Cookware', [[130, 'Frying Pans'], [131, 'Pots & Saucepans']]],
    [31, 'Bedding', [[134, 'Sheets'], [135, 'Duvets']]],
  ]],
  [4, 'Sports & Outdoors', [
    [40, 'Camping', [[140, 'Tents'], [141, 'Sleeping Bags'], [142, 'Backpacks']]],
    [41, 'Fitness', [[145, 'Yoga Mats'], [146, 'Dumbbells']]],
  ]],
  [5, 'Beauty', [[50, 'Skin Care', [[150, 'Moisturizers'], [151, 'Cleansers']]]]],
  [6, 'Toys & Games', [[60, 'Board Games', [[160, 'Strategy Games'], [161, 'Family Games']]]]],
];

export const MOCK_CATEGORY_TREE: CategoryNode[] = TREE.map((t) => node(t, 1));

const brandNames = [
  'Arcwave', 'Bramble & Thread', 'Copper Kettle', 'Driftline', 'Evergreen Trail', 'Fjordlight', 'Glowleaf',
  'Harbor & Pine', 'Kestrel', 'Lindenhof', 'Lumio', 'Meridian Sound', 'Northpeak', 'Novatek', 'Oakhaven',
  'Pebble Play', 'Quillstone', 'Rivermark', 'Solace Skin', 'Trailhead', 'Urbanweave', 'Velora', 'Zentro',
];

export const MOCK_BRAND_PAGE: PageResponse<Ref> = {
  page: 0,
  size: 50,
  totalElements: 184,
  totalPages: 4,
  content: brandNames.map((name, i) => ({ id: 3 + i * 4, name })),
};
