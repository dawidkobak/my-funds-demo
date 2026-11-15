import {Stock} from '../stock';

export interface StockQuote {
  date: string;
  quotes: {
    id: number;
    stock: Stock;
    value: number;
    date: string;
  }[];
}
