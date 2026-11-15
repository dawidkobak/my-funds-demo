export interface User {
  id?: number;
  username: string;
  displayName?: string;
  permissions: string[];
  roles: string[];
}
