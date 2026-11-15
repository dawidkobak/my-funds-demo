import {Wallet} from './wallet';
import {Stock} from './stock';

export type TransactionType = 'BUY' | 'SELL';

export interface Transaction {
  id: number;
  wallet: Wallet;
  amount: number;
  stock: Stock;
  purchaseCost: number;
  purchaseDate: string;
  transactionType: TransactionType;
}
