import { NextRequest, NextResponse } from 'next/server';
import { 
  listAssetFolders, 
  createAssetFolder, 
  renameAssetFolder, 
  deleteAssetFolder 
} from '@/domains/assets/asset.service';

// GET: Fetch list of unique asset folders
export async function GET(req: NextRequest) {
  try {
    const folders = await listAssetFolders();
    return NextResponse.json({ success: true, folders });
  } catch (error: any) {
    console.error('Error fetching asset folders:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

// POST: Create a new virtual folder
export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { folderName } = body;
    if (!folderName) {
      return NextResponse.json({ error: 'Folder name is required' }, { status: 400 });
    }
    const folder = await createAssetFolder(folderName);
    return NextResponse.json({ success: true, folder });
  } catch (error: any) {
    console.error('Error creating asset folder:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

// PUT: Rename a virtual folder
export async function PUT(req: NextRequest) {
  try {
    const body = await req.json();
    const { oldFolderName, newFolderName } = body;
    if (!oldFolderName || !newFolderName) {
      return NextResponse.json({ error: 'Old and new folder names are required' }, { status: 400 });
    }
    await renameAssetFolder(oldFolderName, newFolderName);
    return NextResponse.json({ success: true });
  } catch (error: any) {
    console.error('Error renaming asset folder:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}

// DELETE: Delete a virtual folder (supports soft/hard delete)
export async function DELETE(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const folderName = searchParams.get('folderName');
    const deleteFiles = searchParams.get('deleteFiles') === 'true';

    if (!folderName) {
      return NextResponse.json({ error: 'Folder name is required' }, { status: 400 });
    }

    await deleteAssetFolder(folderName, deleteFiles);
    return NextResponse.json({ success: true });
  } catch (error: any) {
    console.error('Error deleting asset folder:', error);
    return NextResponse.json({ error: error.message }, { status: 500 });
  }
}
