import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { Type } from "typebox";
import { spawn } from "node:child_process";
import path from "node:path";

export default function (pi: ExtensionAPI) {
  pi.registerTool({
    name: "veyra_minecraft_dev_cycle",
    label: "Veyra Minecraft Dev Cycle",
    description:
      "Build/copy the Veyra mod, stop the current Veyra Minecraft process, relaunch Minecraft 26.1.2 with Fabric/VulkanMod, quick-play a singleplayer world, and optionally wait for the Iris compatibility report. Dev-only; do not print tokens.",
    parameters: Type.Object({
      world: Type.Optional(Type.String({ description: "Singleplayer world folder/name. Omit to use the most recently modified save." })),
      skipBuild: Type.Optional(Type.Boolean({ description: "Skip gradle build and only copy the existing jar." })),
      waitReport: Type.Optional(Type.Boolean({ description: "Wait for veyra-iris-compat-report.txt to update." })),
      timeout: Type.Optional(Type.Number({ description: "Seconds to wait for the report when waitReport is true." })),
      noKill: Type.Optional(Type.Boolean({ description: "Do not stop an existing Minecraft process. Usually false." })),
      dryRun: Type.Optional(Type.Boolean({ description: "Prepare the launch command but do not start Minecraft." })),
    }),
    async execute(_toolCallId, params, signal, onUpdate, ctx) {
      const script = path.join(ctx.cwd, "tools", "veyra_minecraft_dev_cycle.py");
      const args = [script];
      if (params.world) args.push("--world", params.world);
      else args.push("--latest-world");
      if (params.skipBuild) args.push("--skip-build");
      if (params.waitReport) args.push("--wait-report");
      if (params.timeout) args.push("--timeout", String(params.timeout));
      if (params.noKill) args.push("--no-kill");
      if (params.dryRun) args.push("--dry-run");

      return await new Promise((resolve, reject) => {
        const child = spawn("python3", args, { cwd: ctx.cwd, stdio: ["ignore", "pipe", "pipe"] });
        let output = "";
        const append = (chunk: Buffer) => {
          const text = chunk.toString();
          output += text;
          onUpdate?.({ content: [{ type: "text", text }] });
        };
        child.stdout.on("data", append);
        child.stderr.on("data", append);
        signal?.addEventListener("abort", () => child.kill("SIGTERM"));
        child.on("error", reject);
        child.on("close", (code) => {
          resolve({
            content: [{ type: "text", text: output || `veyra_minecraft_dev_cycle exited ${code}` }],
            details: { exitCode: code },
            isError: code !== 0,
          });
        });
      });
    },
  });
}
