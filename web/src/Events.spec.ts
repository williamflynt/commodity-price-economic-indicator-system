import { getColorScheme } from './Events'
import { expect, test } from 'vitest';

test('getColorScheme', async () => {
    expect(getColorScheme(null), 'gray');
    expect(getColorScheme('ERROR'), 'red');
    expect(getColorScheme('ANALYZE'), 'blue');
    expect(getColorScheme('ANALYZE-SOMETHING'), 'blue');
    expect(getColorScheme('COLLECT'), 'yellow');
    expect(getColorScheme('COLLECT-SOMETHING'), 'yellow');
    expect(getColorScheme('HELO'), 'green');
    expect(getColorScheme('UNKNOWN'), 'gray');
});
