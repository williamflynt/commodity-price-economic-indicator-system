import { getColorScheme } from './Events'
import { expect, test } from 'vitest';

test('getColorScheme', async () => {
    expect(getColorScheme(null), 'gray', 'returns gray for null');
    expect(getColorScheme('ERROR'), 'red', 'returns red for ERROR');
    expect(getColorScheme('ANALYZE'), 'blue', 'returns blue for ANALYZE');
    expect(getColorScheme('ANALYZE-SOMETHING'), 'blue', 'returns blue for ANALYZE-SOMETHING');
    expect(getColorScheme('COLLECT'), 'yellow', 'returns yellow for COLLECT');
    expect(getColorScheme('COLLECT-SOMETHING'), 'yellow', 'returns yellow for COLLECT-SOMETHING');
    expect(getColorScheme('HELO'), 'green', 'returns green for HELO');
    expect(getColorScheme('UNKNOWN'), 'gray', 'returns gray for unknown eventType');
});
