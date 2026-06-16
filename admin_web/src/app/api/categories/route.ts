import { NextRequest, NextResponse } from 'next/server';
import { listCategories, createCategory, updateCategory, deleteCategory } from '@/domains/categories/category.service';

// GET: Fetch all categories
export async function GET() {
  try {
    const categories = await listCategories();
    return NextResponse.json({ success: true, categories });
  } catch (error: any) {
    console.error('Error fetching categories:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

// POST: Create a new category
export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    if (!body.name) {
      return NextResponse.json({ error: 'Category name is required' }, { status: 400 });
    }

    const category = await createCategory({
      name: body.name,
      order: body.order,
    });

    return NextResponse.json({ success: true, category });
  } catch (error: any) {
    console.error('Error creating category:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

// PUT: Update an existing category
export async function PUT(req: NextRequest) {
  try {
    const body = await req.json();
    if (!body.id) {
      return NextResponse.json({ error: 'Category ID is required for update' }, { status: 400 });
    }

    const category = await updateCategory({
      id: body.id,
      name: body.name,
      order: body.order,
    });

    return NextResponse.json({ success: true, category });
  } catch (error: any) {
    console.error('Error updating category:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

// DELETE: Delete a category
export async function DELETE(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const id = searchParams.get('id');

    if (!id) {
      return NextResponse.json({ error: 'Category ID is required for deletion' }, { status: 400 });
    }

    await deleteCategory(id);
    return NextResponse.json({ success: true, message: 'Category deleted successfully' });
  } catch (error: any) {
    console.error('Error deleting category:', error);
    const status = error.statusCode || 500;
    return NextResponse.json({ error: error.message }, { status });
  }
}
