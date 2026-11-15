export interface ExpenseFolder {
  id: number;
  parentId: number | null;
  name: string;
  depth: number;
  sortOrder: number;
}

export interface ExpenseFolderNode extends ExpenseFolder {
  children: ExpenseFolderNode[];
}

export interface ExpensePaymentSource {
  id: number;
  name: string;
  type: 'bank' | 'cash' | 'manual' | 'legacy';
  isActive: boolean;
}

export interface ExpenseCategory {
  id: number;
  name: string;
  color: string;
  icon: string;
  isActive: boolean;
}

export interface ExpenseTag {
  id: number;
  name: string;
  isActive: boolean;
}

export interface ExpenseCurrency {
  id: number;
  code: string;
  name: string;
}

export type ExpenseCountingStatus =
  | 'included'
  | 'excluded_internal_transfer'
  | 'excluded_business'
  | 'excluded_investment'
  | 'excluded_cash_transfer'
  | 'excluded_reimbursed'
  | 'excluded_duplicate';

export interface ExpenseItem {
  id: number;
  folder: Pick<ExpenseFolder, 'id' | 'name'>;
  paymentSource: Pick<ExpensePaymentSource, 'id' | 'name'>;
  category: Pick<ExpenseCategory, 'id' | 'name' | 'color' | 'icon'> | null;
  currency: Pick<ExpenseCurrency, 'id' | 'code'>;
  name: string;
  note: string | null;
  amount: number;
  month: string;
  date: string | null;
  type: 'expense' | 'refund' | 'adjustment';
  countingStatus: ExpenseCountingStatus;
  tags: ExpenseTag[];
}

export interface ExpenseBootstrap {
  folders: ExpenseFolder[];
  categories: ExpenseCategory[];
  tags: ExpenseTag[];
  paymentSources: ExpensePaymentSource[];
  currencies: ExpenseCurrency[];
  legacyMigration: {
    totalRows: number;
    importedRows: number;
    skippedRows: number;
    committedAt: string;
  } | null;
}

export interface ExpenseSearchRequest {
  folderId: number | null;
  search: string;
  dateFrom: string;
  dateTo: string;
  categoryIds: number[];
  tagIds: number[];
  paymentSourceIds: number[];
  currencyCodes: string[];
  includeExcluded: boolean;
  sort: string;
  direction: 'asc' | 'desc';
  page: number;
  pageSize: number;
}

export interface ExpenseSearchResponse {
  items: ExpenseItem[];
  totalCount: number;
  page: number;
  pageSize: number;
  totals: Array<{currency: string; amount: number}>;
}

export interface ExpenseReportPoint {
  name?: string;
  month?: string;
  color?: string;
  icon?: string;
  currency: string;
  amount: number;
}

export interface ExpenseReportResponse {
  periodGranularity: 'day' | 'month';
  totals: Array<{currency: string; amount: number}>;
  monthly: ExpenseReportPoint[];
  categories: ExpenseReportPoint[];
  paymentSources: ExpenseReportPoint[];
  tags: ExpenseReportPoint[];
}

export interface ExpenseImportRow {
  rowNumber: number;
  folder: string | null;
  folderId?: number;
  paymentSource: string | null;
  paymentSourceId: number | null;
  category: string | null;
  categoryId?: number;
  tags: string[];
  tagIds?: number[];
  name: string;
  note: string | null;
  amount: number;
  currency: string;
  month: string;
  date: string | null;
  type: 'expense' | 'refund' | 'adjustment';
  countingStatus: ExpenseCountingStatus;
  fingerprint: string;
  warnings: string[];
  duplicate: boolean;
  selected: boolean;
}

export interface ExpenseImportPreview {
  format: string;
  fileName: string;
  checksum: string;
  rows: ExpenseImportRow[];
  warnings: string[];
  duplicates: number;
  missingSources: number;
  totals: Array<{currency: string; amount: number}>;
  convertedJson: unknown;
}

// Legacy components remain in the repository but are no longer routed.
export interface SubExpense {
  id: number;
  name: string;
  createdAt: string;
  value: number;
}

export interface Expense {
  id: number;
  name: string;
  value: number;
  color: string;
  createdAt: string;
  subExpenses: SubExpense[];
  tags?: string[];
}
