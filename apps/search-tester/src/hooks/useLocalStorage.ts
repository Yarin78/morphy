import { useCallback, useState } from 'react';

export function useLocalStorage(key: string, initialValue: boolean): [boolean, (value: boolean) => void] {
  const [value, setValue] = useState<boolean>(() => {
    try {
      return localStorage.getItem(key) === 'true';
    } catch {
      return initialValue;
    }
  });

  const setValueWithStorage = useCallback(
    (newValue: boolean) => {
      setValue(newValue);
      try {
        localStorage.setItem(key, String(newValue));
      } catch {
        // ignore
      }
    },
    [key]
  );

  return [value, setValueWithStorage];
}
