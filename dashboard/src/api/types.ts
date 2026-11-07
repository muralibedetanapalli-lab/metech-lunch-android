// Mirrors backend/src/main/kotlin/.../admin/AdminModels.kt and core:model enums.

export type UserRole = "customer" | "delivery_partner" | "kitchen_staff" | "owner";

export type OrderStatus =
  | "pending_payment"
  | "confirmed"
  | "preparing"
  | "out_for_delivery"
  | "delivered"
  | "partially_delivered"
  | "delivery_failed"
  | "cancel_requested"
  | "cancelled"
  | "payment_failed";

export type PaymentStatus = "pending" | "verified" | "failed" | "refunded";
export type RefundStatus = "requested" | "approved" | "rejected" | "completed";
export type BatchStatus = "unassigned" | "assigned" | "picked_up" | "in_transit" | "delivered" | "partially_delivered" | "failed";

export interface ApiError {
  code: string;
  message: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface UserProfile {
  id: string;
  name: string;
  phone: string;
  email?: string;
  role: UserRole;
}

export interface KitchenSettings {
  id: string;
  name: string;
  address: string;
  timezone: string;
  cutoffTime: string;
  today: string;
}

export interface AdminDropPoint {
  id: string;
  name: string;
  instructions?: string;
  active: boolean;
}

export interface AdminOffice {
  id: string;
  name: string;
  address: string;
  active: boolean;
  dropPoints: AdminDropPoint[];
}

export interface AdminMenuItem {
  id: string;
  name: string;
  description: string;
  imageUrl?: string;
  pricePaise: number;
  dietaryTags: string[];
  allergens: string[];
  active: boolean;
}

export interface SaveMenuItemRequest {
  name: string;
  description: string;
  imageUrl?: string | null;
  pricePaise: number;
  dietaryTags: string[];
  allergens: string[];
  active: boolean;
}

export interface DailyMenuEntry {
  menuItemId: string;
  name: string;
  pricePaise: number;
  capacity?: number | null;
  available: boolean;
  reserved: number;
}

export interface DailyMenuView {
  date: string;
  published: DailyMenuEntry[];
}

export interface DailyMenuLine {
  menuItemId: string;
  pricePaise: number;
  capacity: number | null;
  available: boolean;
}

export interface AdminOrderLine {
  name: string;
  quantity: number;
}

export interface AdminOrder {
  id: string;
  deliveryDate: string;
  createdAt: string;
  status: OrderStatus;
  customerName: string;
  customerPhone: string;
  officeId: string;
  officeName: string;
  dropPointName?: string;
  lines: AdminOrderLine[];
  totalPaise: number;
  paymentStatus?: PaymentStatus;
  paymentReference?: string;
  refundStatus?: RefundStatus;
  batchId?: string;
}

export interface MealTotal {
  menuItemId: string;
  name: string;
  quantity: number;
}

export interface PackingOrder {
  orderId: string;
  customerName: string;
  lines: AdminOrderLine[];
  status: OrderStatus;
}

export interface PackingGroup {
  officeId: string;
  officeName: string;
  officeAddress: string;
  dropPointId?: string;
  dropPointName?: string;
  mealTotals: MealTotal[];
  orders: PackingOrder[];
}

export interface PrepView {
  date: string;
  cutoffPassed: boolean;
  mealTotals: MealTotal[];
  totalMeals: number;
  confirmedOrders: number;
  awaitingPrep: number;
  pendingCancellations: number;
  groups: PackingGroup[];
}

export interface CountResult {
  updated: number;
}

export interface AdminBatch {
  id: string;
  deliveryDate: string;
  officeName: string;
  dropPointName?: string;
  status: BatchStatus;
  partnerId?: string;
  partnerName?: string;
  coordinatorName?: string;
  coordinatorPhone?: string;
  coordinatorOtp?: string;
  orderCount: number;
  totalMeals: number;
  pickupCount?: number;
  orders: PackingOrder[];
}

export interface AdminIssue {
  id: number;
  batchId: string;
  officeName: string;
  partnerName: string;
  orderIds: string[];
  reason: string;
  partial: boolean;
  createdAt: string;
}

export interface AdminUser {
  id: string;
  name: string;
  phone: string;
  email?: string;
  role: UserRole;
  active: boolean;
  createdAt: string;
  orderCount?: number;
}

export interface DaySummary {
  date: string;
  orders: number;
  meals: number;
  salesPaise: number;
  refundedPaise: number;
}

export interface ReportView {
  from: string;
  to: string;
  days: DaySummary[];
  statusCounts: Record<string, number>;
  mealTotals: MealTotal[];
  officeTotals: Record<string, number>;
  totalSalesPaise: number;
  totalRefundedPaise: number;
  pendingRefunds: number;
}

export interface OverviewView {
  date: string;
  orderingOpen: boolean;
  cutoffTime: string;
  statusCounts: Record<string, number>;
  meals: number;
  salesPaise?: number | null;
  pendingRefunds?: number | null;
  openIssues: number;
  unassignedBatches: number;
}

export interface AuditEntry {
  id: number;
  actorName?: string;
  action: string;
  entity: string;
  entityId: string;
  details?: string;
  createdAt: string;
}

export interface TrackedLocation {
  latitude: number;
  longitude: number;
  accuracyMeters?: number | null;
  speedMps?: number | null;
  recordedAt: string;
}

export interface TrackingBatch {
  id: string;
  status: BatchStatus;
  partnerName?: string | null;
  partnerPhone?: string | null;
  pickedUpAt?: string | null;
  totalOrders: number;
  deliveredOrders: number;
}

export interface OrderTracking {
  order: AdminOrder;
  batch?: TrackingBatch | null;
  live: boolean;
  location?: TrackedLocation | null;
  trail: TrackedLocation[];
}
