import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Integration } from '../src/lib/constants';

const {
  mockIsNonInteractiveEnvironment,
  mockReadEnvironment,
  mockClackSelect,
  mockClackSuccess,
} = vi.hoisted(() => ({
  mockIsNonInteractiveEnvironment: vi.fn(),
  mockReadEnvironment: vi.fn(() => ({})),
  mockClackSelect: vi.fn(),
  mockClackSuccess: vi.fn(),
}));

vi.mock('../src/utils/environment', async () => {
  const actual = await vi.importActual<
    typeof import('../src/utils/environment')
  >('../src/utils/environment');

  return {
    ...actual,
    isNonInteractiveEnvironment: mockIsNonInteractiveEnvironment,
    readEnvironment: mockReadEnvironment,
  };
});

vi.mock('../src/utils/clack', () => ({
  default: {
    select: mockClackSelect,
    isCancel: vi.fn(() => false),
    intro: vi.fn(),
    outro: vi.fn(),
    cancel: vi.fn(),
    note: vi.fn(),
    spinner: vi.fn(() => ({ start: vi.fn(), stop: vi.fn() })),
    log: {
      success: mockClackSuccess,
      error: vi.fn(),
    },
  },
}));

vi.mock('../src/lib/config', async () => {
  const actual =
    await vi.importActual<typeof import('../src/lib/config')>(
      '../src/lib/config',
    );

  return {
    ...actual,
    INTEGRATION_CONFIG: {
      [Integration.nodejs]: {
        ...actual.INTEGRATION_CONFIG[Integration.nodejs],
        detect: vi.fn(async () => false),
      },
    },
  };
});

import { getIntegrationForSetup } from '../src/run';

describe('getIntegrationForSetup', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fails fast instead of prompting in non-interactive mode', async () => {
    mockIsNonInteractiveEnvironment.mockReturnValue(true);

    await expect(getIntegrationForSetup()).rejects.toThrow(
      'Unable to detect the integration in non-interactive mode.',
    );
    expect(mockClackSelect).not.toHaveBeenCalled();
  });
});
