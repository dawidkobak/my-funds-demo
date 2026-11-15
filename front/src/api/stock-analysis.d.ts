import {Stock} from './stock';

export type StockAnalysisStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED';

export type StockAnalysisDecision =
  | 'ODRZUCIC'
  | 'OBSERWOWAC'
  | 'GLEBSZA_ANALIZA'
  | 'KANDYDAT_DO_PORTFELA';

export interface StockAnalysis {
  id: number;
  stock: Stock | null;
  companyName: string;
  ticker: string | null;
  country: string | null;
  industry: string | null;
  sourceMarkdown?: string;
  sourceFilePath?: string | null;
  status: StockAnalysisStatus;
  model: string;
  promptVersion: string;
  reportMarkdown?: string | null;
  totalScore: number | null;
  decision: StockAnalysisDecision | null;
  metadataJson?: unknown | null;
  errorMessage: string | null;
  createdAt: string;
  processingStartedAt: string | null;
  completedAt: string | null;
  updatedAt: string;
}

export interface StockAnalysisCreatePayload {
  stock: Pick<Stock, 'id'> | null;
  companyName: string;
  ticker: string | null;
  country: string | null;
  industry: string | null;
  sourceMarkdown: string;
}
