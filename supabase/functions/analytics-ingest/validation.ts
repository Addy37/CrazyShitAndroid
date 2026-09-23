const deviceModelValue = /^[\p{L}\p{N} ._+()/\-]{1,80}$/u;
const androidVersionValue = /^(?:[0-9]{1,2}(?:\.[0-9]{1,2})?|Unknown)$/;

export function validDeviceModel(value: unknown): boolean {
  return typeof value === "string" && value === value.trim() && deviceModelValue.test(value);
}

export function validAndroidVersion(value: unknown): boolean {
  return typeof value === "string" && androidVersionValue.test(value);
}
