import process from 'node:process'
process.env.NODE_ENV = 'test'
const { startVitest, parseCLI } = await import('vitest/node')
const { filter, options } = parseCLI(['vitest', ...process.argv.slice(2)])
const ctx = await startVitest('test', filter, { ...options, watch: false })
if (!ctx) process.exitCode = 1
else {
  process.exitCode =
    !ctx.state.getFiles().length ||
    ctx.state.getUnhandledErrors().length ||
    ctx.state.getFiles().some((f) => f.result?.state === 'fail')
      ? 1
      : 0
  await ctx.close()
}
