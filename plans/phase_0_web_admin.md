Khởi tạo Module admin_web (Phase 0: Setup & Foundation)
Tạo module Next.js độc lập (admin_web) trong thư mục gốc của dự án để làm công cụ Web Admin quản lý và thiết kế template, tối ưu hóa chi phí vận hành ở mức $0/tháng (Sử dụng Next.js, Supabase, Cloudflare R2 và Vercel).

User Review Required
IMPORTANT

Module Độc lập: admin_web là một module Web độc lập viết bằng Next.js + TypeScript, không tích hợp vào hệ thống build Gradle của Android. Do đó, các dependency Kotlin/Java của Android sẽ không bị ảnh hưởng.
Cấu hình Supabase & R2: Bạn cần tạo tài khoản Supabase (Free tier) và Cloudflare R2 (Free tier) để lấy các API keys cấu hình vào file .env.local. Chúng tôi sẽ tạo sẵn file mẫu .env.local.example để bạn dễ điền.
Bản quyền Fabric.js: Chúng tôi sẽ cài đặt fabric phiên bản mới nhất để hỗ trợ đầy đủ các tính năng Canvas nâng cao.
Open Questions
NOTE

Bạn có muốn cấu hình Google OAuth ngay trong Phase 0 hay chúng ta sẽ dùng Email/Password Auth đơn giản trước để dễ dev offline? (Google OAuth cần cấu hình Google Cloud Console Credential).
Bạn có email Whitelist cụ thể nào để cấu hình chặn quyền truy cập (chỉ cho phép email của bạn đăng nhập vào Admin Web) không?
Proposed Changes
Tất cả các thay đổi sẽ nằm trong thư mục mới: C:/Users/Toshiba/Desktop/imageProduction-main/admin_web/.

[NEW] Next.js 14/15 App: admin_web
Khởi tạo cấu trúc dự án Next.js và cài đặt các thư viện cần thiết.

[NEW]
admin_web/.env.local.example
File mẫu chứa cấu hình môi trường cho Supabase và Cloudflare R2.

[NEW]
admin_web/types/cloud-template.ts
Định nghĩa TypeScript types khớp hoàn toàn với CloudTemplate.kt trong core-domain để đảm bảo tính tương thích ngược khi trao đổi JSON.

[NEW]
admin_web/lib/supabase.ts
Khởi tạo Supabase Client (cho phía Client Component) và Server Client (cho API route / Server Component).

[NEW]
admin_web/lib/r2.ts
Khởi tạo client AWS S3 SDK cấu hình kết nối trực tiếp đến Cloudflare R2.

[NEW]
admin_web/supabase_schema.sql
File script SQL để bạn copy vào Supabase SQL Editor nhằm tạo các bảng categories, templates, assets và các index tối ưu.

Verification Plan
Automated Tests
Chạy lệnh npm run build trong thư mục admin_web để đảm bảo toàn bộ project Next.js compile thành công, không gặp lỗi TypeScript hay Webpack/Rspack.
Manual Verification
Chạy npm run dev để kiểm tra project chạy được local tại port 3000.
Xác nhận các file helper client kết nối (supabase.ts và r2.ts) không gây lỗi biên dịch.