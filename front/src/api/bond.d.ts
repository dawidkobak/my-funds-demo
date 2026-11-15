import {Wallet} from './wallet';

export type BondType =
  | 'EDO'
  | 'COI'
  | 'ROD'
  | 'TOS'
  | 'OTS'
  | 'ROR'
  | 'DOR'
  | 'ROS';

export interface BondIssue {
  id: number;
  symbol: string;
  bondType: BondType;
  nominalValue: number;
  saleStartDate: string;
  saleEndDate: string;
  maturityYears: number;
  issueLetterUrl: string | null;
  interestTableUrl: string | null;
}

export interface BondPosition {
  id: number;
  wallet: Wallet;
  bondIssue: BondIssue;
  availableUnits: number;
  blockedUnits: number;
  totalUnits: number;
  purchaseDate: string;
  maturityDate: string;
  purchaseCost: number;
  sourceFilePath: string | null;
  sourceValueDate: string | null;
  sourceCurrentValue: number | null;
  accruedInterest: number;
  currentUnitValue: number;
  currentTotalValue: number;
}
