import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { CategoryNode } from '../../models/category.model';

/**
 * Category tree with native <details> disclosure (keyboard accessible, no script needed).
 * Leaves link to the product search filtered on that category.
 */
@Component({
  selector: 'app-category-tree',
  imports: [NgTemplateOutlet, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './category-tree.html',
  styleUrl: './category-tree.scss',
})
export class CategoryTree {
  readonly nodes = input.required<CategoryNode[]>();
}
