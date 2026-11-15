export type WalletType = 'STOCK' | 'BOND' | 'NET_WORTH';

export interface Wallet {
  id: number;
  name: string;
  walletType: WalletType;
}
