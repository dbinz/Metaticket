export const storageKeys = {
  userToken: 'USER_TOKEN',
  accessoryCart: 'ACCESSORY_CART',
  // Persisted accessory checkout flow (step index + order/payment ids + discount/customer) so a
  // page reload resumes the correct step (information / payment / completed).
  accessoryCheckout: 'ACCESSORY_CHECKOUT',
};
