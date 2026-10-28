export interface PageResult<T> { items: T[]; nextCursor: string | null }
export interface ApiErrorBody { code?: string; message?: string; fields?: Record<string, string> }
export interface User {
  id: string; email: string; fullName: string; avatarUrl?: string | null;
  admin: boolean; verifiedExpert: boolean; role?: string; province?: string | null;
}
export interface AuthResponse { idToken: string; refreshToken: string; expiresIn: number; user: User }
export interface TokenResponse { idToken: string; refreshToken: string; expiresIn: number }
export interface Session { idToken: string; refreshToken: string; expiresAt: number; user: User }
export type ReportStatus = 'NEW' | 'RESOLVED' | 'DISMISSED'
export interface AdminReport {
  id: string; type: 'POST' | 'COMMENT'; postId: string; targetId: string;
  reporterId: string; reason: string; detail?: string | null; status: ReportStatus;
  createdAt: number; resolvedAt?: number | null;
}
export interface AdminFeedback {
  id: string; userId: string; scanId: string; scanDiseaseCode?: string | null;
  scanConfidence?: number | null; engine?: string | null; imageUrl?: string | null;
  reason: string; suggestedDiseaseCode?: string | null; description?: string | null;
  status: string; createdAt: number; attachments?: ImageRef[]; resolution?: string | null; resolvedAt?: number | null;
}
export interface AdminPost {
  id: string; authorId: string; province?: string | null; topic: string; title: string; content: string;
  images: ImageRef[]; likeCount: number; commentCount: number; createdAt: number; updatedAt: number; hidden: boolean;
}
export interface AdminStats {
  reports: { total: number; pending: number; resolvedToday: number; today: number; yesterday: number };
  feedbacks: { total: number; pending: number; reviewing: number; resolved: number };
  posts: { total: number; visible: number; hidden: number; today: number };
}
export interface PublicUser {
  id: string; name: string; avatarUrl?: string | null; role?: string;
  verifiedExpert: boolean; province?: string | null;
}
export interface ImageRef { url: string; publicId?: string }
export interface Post {
  id: string; author: PublicUser; province?: string | null; topic: string; title: string;
  content: string; images: ImageRef[]; likeCount: number; commentCount: number;
  liked: boolean; saved: boolean; mine: boolean; createdAt: number; updatedAt: number;
}
export interface Comment {
  id: string; parentId?: string | null; author: PublicUser; text: string;
  likeCount: number; badge?: string | null; createdAt: number; replies: Comment[];
}
export interface Disease {
  id: string; version: number; name: string; scientificName?: string;
  description: string; part: string; cause: string; common: boolean;
  symptoms: string[]; conditions: string[]; prevention: string[]; treatment: string[];
  imageUrl?: string | null; imagePublicId?: string | null; saved?: boolean;
  related?: Disease[]; hidden?: boolean; adminEdited?: boolean;
}
export interface AdminUser {
  id: string; email: string; fullName: string; phone?: string | null; province?: string | null;
  district?: string | null; role: string; verifiedExpert: boolean; locked: boolean;
  verifiedBy?: string | null; verifiedAt?: number | null; lockedBy?: string | null; lockedAt?: number | null;
  createdAt: number; updatedAt: number;
}
export interface AdminUserDetail { user: AdminUser; stats: { scans: number; fields: number; unreadAlerts: number } }
export interface DiseaseDraft {
  name: string; scientificName: string; description: string; part: string; cause: string; common: boolean;
  symptoms: string[]; conditions: string[]; prevention: string[]; treatment: string[];
}
export interface CampaignDraft { title: string; body: string; audience: 'ALL' | 'PROVINCE' | 'SELECTED'; province?: string; userIds?: string[] }
export interface Campaign extends CampaignDraft {
  id: string; status: string; createdBy: string; createdAt: number; startedAt?: number | null; sentAt?: number | null;
  recipientCount: number; delivered: number; failed: number;
}
export interface CampaignPreview { count: number; recipients: { id: string; name: string; province?: string | null }[] }
export interface AuditEntry { id: string; actorId: string; action: string; targetType: string; targetId: string; detail: string; createdAt: number }
export interface DiseaseCount { code: string; name: string; cases: number }
export interface RiskArea {
  province: string; district: string; cases: number; level: 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH';
  lastAt?: number | null; diseases: DiseaseCount[]; dominantStage?: string | null;
}
export interface RiskAreaInfo extends RiskArea {
  fromDate: string; toDate: string; advice: string;
  weather?: { temperature?: number; description?: string } | null;
}
