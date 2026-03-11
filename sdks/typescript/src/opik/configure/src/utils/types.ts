export type PostHogProjectData = Record<string, unknown>;

export type DeploymentTypeOption = 'cloud' | 'self-hosted' | 'local';

export type PackageManagerName = 'bun' | 'yarn' | 'pnpm' | 'npm';

export type PreselectedProject = {
  project: PostHogProjectData;
  authToken: string;
};

export type WizardOptions = {
  /**
   * Whether to enable debug mode.
   */
  debug: boolean;

  /**
   * Whether to force install the SDK package to continue with the installation in case
   * any package manager checks are failing (e.g. peer dependency versions).
   *
   * Use with caution and only if you know what you're doing.
   *
   * Does not apply to all wizard flows (currently NPM only)
   */
  forceInstall: boolean;

  /**
   * The directory to run the wizard in.
   */
  installDir: string;

  /**
   * Whether to select the default option for all questions automatically.
   */
  default: boolean;

  /**
   * Whether to configure for local deployment.
   * When true, skips API key and workspace prompts and uses local defaults.
   */
  useLocal?: boolean;

  /**
   * Deployment target to configure without prompting.
   */
  deploymentType?: DeploymentTypeOption;

  /**
   * Base URL for a local or self-hosted Opik instance.
   */
  url?: string;

  /**
   * Opik API key to use for cloud or self-hosted configuration.
   */
  apiKey?: string;

  /**
   * Workspace name to use for cloud or self-hosted configuration.
   */
  workspace?: string;

  /**
   * Trust and probe a custom setup URL without prompting for confirmation.
   */
  trustUrl?: boolean;

  /**
   * Project name to configure without prompting.
   */
  projectName?: string;

  /**
   * Package manager to use when auto-detection is ambiguous.
   */
  packageManager?: PackageManagerName;
};

export interface Feature {
  id: string;
  prompt: string;
  enabledHint?: string;
  disabledHint?: string;
}

export type FileChange = {
  filePath: string;
  oldContent?: string;
  newContent: string;
};

export type AIModel = 'o4-mini' | 'gemini-2.5-flash' | 'gemini-2.5-pro';
