export const validationRegex = {
  fullName: /^[\p{L}\p{M}\s]+$/u,
  phoneNumber: /^(?:\+84|84|0)(3|5|7|8|9)[0-9]{8}$/,
  email: /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/i,
};
