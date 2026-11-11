class StoreManager {
  private static instance: StoreManager
  private static scope: string | undefined

  private constructor(id: string) {
    StoreManager.scope = id
  }

  public static setScope(id: string): void {
    if (StoreManager.scope && StoreManager.scope !== id) {
      throw new Error(
        `StoreManager scope is already set to: "${StoreManager.scope}". Cannot set to: "${id}"`
      )
    }
    StoreManager.scope = id
  }

  public static getInstance(): StoreManager {
    if (!StoreManager.instance) {
      StoreManager.instance = new StoreManager('') // dummy id
    }
    return StoreManager.instance
  }

  private getKey(key: string): string {
    if (!StoreManager.scope) {
      throw new Error(
        'StoreManager is not initialized. Call init() with a session id first.'
      )
    }
    return `metaticket-${key}-${StoreManager.scope}`
  }

  public setItem(key: string, value: any): void {
    const scopedKey = this.getKey(key)
    localStorage.setItem(scopedKey, value)
  }

  public getItem(key: string): any {
    const scopedKey = this.getKey(key)
    return localStorage.getItem(scopedKey)
  }

  public removeItem(key: string): void {
    const scopedKey = this.getKey(key)
    localStorage.removeItem(scopedKey)
  }
}

export { StoreManager }
